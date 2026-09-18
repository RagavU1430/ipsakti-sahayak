$rootDir = Resolve-Path "$PSScriptRoot\.."
if (Test-Path "$rootDir\.env") {
    Get-Content "$rootDir\.env" | ForEach-Object {
        $line = $_.Trim()
        if ($line -and -not $line.StartsWith('#') -and $line.Contains('=')) {
            $parts = $line.Split('=', 2)
            $varName = $parts[0].Trim()
            $varVal = $parts[1].Trim()
            if (($varVal.StartsWith('"') -and $varVal.EndsWith('"')) -or ($varVal.StartsWith("'") -and $varVal.EndsWith("'"))) {
                $varVal = $varVal.Substring(1, $varVal.Length - 2)
            }
            [System.Environment]::SetEnvironmentVariable($varName, $varVal, "Process")
        }
    }
}
Set-Location $PSScriptRoot
& .\mvnw.cmd spring-boot:run
