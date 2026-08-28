from __future__ import annotations
import json, re, os, shutil, subprocess
from pathlib import Path
import fitz
from bs4 import BeautifulSoup

SCRATCH_DIR = Path("C:/Users/Ragav U/.gemini/antigravity-ide/brain/0df2255d-8e82-47ba-86f1-8e88ac4b64f5/scratch")

def run_windows_ocr(temp_folder: Path) -> list[str]:
    ps_script = f"""
    [Windows.Media.Ocr.OcrEngine, Windows.Media.Ocr, ContentType=WindowsRuntime] | Out-Null
    [Windows.Graphics.Imaging.BitmapDecoder, Windows.Graphics.Imaging, ContentType=WindowsRuntime] | Out-Null
    [Windows.Storage.StorageFile, Windows.Storage, ContentType=WindowsRuntime] | Out-Null
    [Windows.Graphics.Imaging.SoftwareBitmap, Windows.Graphics.Imaging, ContentType=WindowsRuntime] | Out-Null
    [Windows.Media.Ocr.OcrResult, Windows.Media.Ocr, ContentType=WindowsRuntime] | Out-Null
    [Windows.Storage.Streams.IRandomAccessStream, Windows.Storage, ContentType=WindowsRuntime] | Out-Null
    Add-Type -AssemblyName "System.Runtime.WindowsRuntime"

    $folder = "{temp_folder.as_posix()}".Replace('/', '\\')
    $files = Get-ChildItem -Path $folder -Filter "*.png" | Sort-Object Name

    $global:asTaskMethod = [System.WindowsRuntimeSystemExtensions].GetMethods() | Where-Object {{
        $_.Name -eq 'AsTask' -and 
        $_.GetParameters().Length -eq 1 -and 
        $_.GetParameters()[0].ParameterType.Name.StartsWith('IAsyncOperation')
    }} | Select-Object -First 1

    if ($global:asTaskMethod -eq $null) {{
        throw "AsTask method not found"
    }}

    function Await-Op($asyncOp, $resultType) {{
        $genericMethod = $global:asTaskMethod.MakeGenericMethod($resultType)
        $task = $genericMethod.Invoke($null, @($asyncOp))
        $task.Wait()
        return $task.Result
    }}

    $engine = [Windows.Media.Ocr.OcrEngine]::TryCreateFromUserProfileLanguages()
    if ($engine -eq $null) {{
        throw "Failed to create OCR Engine"
    }}

    foreach ($file in $files) {{
        Write-Output "---PAGE_START---"
        try {{
            $fileOp = [Windows.Storage.StorageFile]::GetFileFromPathAsync($file.FullName)
            $storageFile = Await-Op $fileOp ([Windows.Storage.StorageFile])

            $streamOp = $storageFile.OpenAsync([Windows.Storage.FileAccessMode]::Read)
            $stream = Await-Op $streamOp ([Windows.Storage.Streams.IRandomAccessStream])

            $decoderOp = [Windows.Graphics.Imaging.BitmapDecoder]::CreateAsync($stream)
            $decoder = Await-Op $decoderOp ([Windows.Graphics.Imaging.BitmapDecoder])

            $softwareBitmapOp = $decoder.GetSoftwareBitmapAsync()
            $softwareBitmap = Await-Op $softwareBitmapOp ([Windows.Graphics.Imaging.SoftwareBitmap])

            $ocrOp = $engine.RecognizeAsync($softwareBitmap)
            $ocrResult = Await-Op $ocrOp ([Windows.Media.Ocr.OcrResult])

            Write-Output $ocrResult.Text
        }} catch {{
            Write-Output "ERROR: $_"
        }}
        Write-Output "---PAGE_END---"
    }}
    """
    result = subprocess.run(
        ["powershell", "-Command", ps_script],
        capture_output=True,
        text=True,
        encoding="cp1252",
        errors="replace"
    )
    
    raw_output = result.stdout
    pages_text = []
    parts = raw_output.split("---PAGE_START---")
    for part in parts[1:]:
        content = part.split("---PAGE_END---")[0].strip()
        pages_text.append(content)
    return pages_text

def extract(path: Path) -> dict:
    pages = []
    is_pdf = path.suffix.lower() == ".pdf"
    
    # 1. Normal Extraction
    if is_pdf:
        with fitz.open(path) as doc:
            for number, page in enumerate(doc, 1):
                pages.append({"page": number, "text": page.get_text("text", sort=True)})
    else:
        text = BeautifulSoup(path.read_text(encoding="utf-8", errors="replace"), "html.parser").get_text("\n", strip=True)
        pages.append({"page": 1, "text": text})
    
    chars = sum(len(p["text"].strip()) for p in pages)
    ocr_required = bool(is_pdf and pages and chars / len(pages) < 80)
    
    # 2. OCR Fallback if required
    if ocr_required:
        cache_path = path.with_suffix(".ocr.json")
        if cache_path.exists():
            print(f"Loaded OCR text from cache for {path.name}")
            try:
                pages = json.loads(cache_path.read_text(encoding="utf-8"))
                ocr_required = False
            except Exception as e:
                print(f"Error reading cache, will re-run OCR: {e}")
        
        if ocr_required:
            print(f"Running UWP OCR for {path.name}...")
            temp_folder = SCRATCH_DIR / f"ocr_temp_{path.stem}"
            temp_folder.mkdir(parents=True, exist_ok=True)
            
            try:
                # Render each page to PNG
                with fitz.open(path) as doc:
                    for idx, page in enumerate(doc):
                        pix = page.get_pixmap(dpi=150)
                        pix.save(str(temp_folder / f"page_{idx:05d}.png"))
                
                # Perform OCR on PNGs
                ocr_pages = run_windows_ocr(temp_folder)
                
                # Populate page dictionaries
                pages = []
                for idx, text in enumerate(ocr_pages, 1):
                    pages.append({"page": idx, "text": text})
                
                # Cache results
                cache_path.write_text(json.dumps(pages, indent=2, ensure_ascii=False), encoding="utf-8")
                ocr_required = False
                
                # Compute and display OCR metrics
                total_pages = len(pages)
                total_chars = sum(len(p["text"].strip()) for p in pages)
                avg_chars = total_chars / total_pages if total_pages > 0 else 0
                blank_pages = sum(1 for p in pages if not p["text"].strip())
                coverage = ((total_pages - blank_pages) / total_pages) * 100 if total_pages > 0 else 0
                print(f"OCR Complete for {path.name}: Pages={total_pages}, Chars={total_chars}, AvgChars/Page={avg_chars:.1f}, BlankPages={blank_pages}, Coverage={coverage:.1f}%")
            
            finally:
                if temp_folder.exists():
                    shutil.rmtree(temp_folder)
                    
    return {"pages": pages, "page_count": len(pages), "ocr_required": ocr_required}

def clean_text(text: str) -> str:
    text = text.replace("\u00ad", "").replace("\r", "\n")
    text = re.sub(r"[ \t]+", " ", text)
    return re.sub(r"\n{3,}", "\n\n", text).strip()


