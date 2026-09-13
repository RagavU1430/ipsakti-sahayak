import os
import re
import sys

# Define the project root
project_root = r"C:\Users\Ragav U\OneDrive\Desktop\Ragav Folder\Projects\SIH\ipsakti-sahayak\Frontend"

# List of files to check (relative to project_root)
files_to_check = [
    # API layer
    r"src\api\client.ts",
    r"src\api\questions.ts",
    r"src\api\conversations.ts",
    r"src\api\formulations.ts",
    r"src\api\regulatory.ts",
    r"src\api\tk.ts",
    r"src\api\voice.ts",
    r"src\api\auth.ts",
    r"src\api\types.ts",
    # Pages
    r"src\pages\AskPage.tsx",
    r"src\pages\HomePage.tsx",
    r"src\pages\LoginPage.tsx",
    r"src\pages\HistoryPage.tsx",
    r"src\pages\ConversationDetailPage.tsx",
    r"src\pages\FormulationPage.tsx",
    r"src\pages\RegulatoryPage.tsx",
    r"src\pages\TkOverlapPage.tsx",
    r"src\pages\AccountPage.tsx",
    r"src\pages\AboutPage.tsx",
    # Components
    r"src\components\ErrorBoundary.tsx",
    r"src\components\ErrorNotice.tsx",
    r"src\components\ResultCard.tsx",
    r"src\components\Evidence.tsx",
    r"src\components\FeedbackModal.tsx",
    r"src\components\FormulationReportChatbot.tsx",
    r"src\components\VoiceChatOverlay.tsx",
    r"src\components\VoiceAssistantControls.tsx",
    r"src\components\AudioPlayerBar.tsx",
    r"src\components\LoadingSteps.tsx",
    r"src\components\KeyboardHelpOverlay.tsx",
    r"src\components\FormControls.tsx",
    r"src\components\FormattedText.tsx",
    # Hooks
    r"src\hooks\useSpeechRecognition.ts",
    r"src\hooks\useSpeechSynthesis.ts",
    r"src\hooks\useVoiceRecorder.ts",
    r"src\hooks\useTheme.ts",
    # App
    r"src\App.tsx",
    r"src\main.tsx",
    # Config
    r"vite.config.ts",
    r"tsconfig.json",
    r"package.json",
    r".env",
    r".env.local"
]

def read_file_lines(file_path):
    full_path = os.path.join(project_root, file_path)
    try:
        with open(full_path, 'r', encoding='utf-8') as f:
            lines = f.readlines()
        return lines, len(lines)
    except FileNotFoundError:
        return None, 0
    except Exception as e:
        print(f"Error reading {file_path}: {e}")
        return None, 0

def add_issue(issues, file_path, line_num, severity, description, fix):
    issues.append({
        "file": file_path,
        "line": line_num,
        "severity": severity,
        "description": description,
        "fix": fix
    })

def main():
    issues = []
    
    for file_path in files_to_check:
        lines, total_lines = read_file_lines(file_path)
        if lines is None:
            add_issue(issues, file_path, 0, "HIGH", "File not found or cannot be read", "Check if the file exists and the path is correct")
            continue

        content = ''.join(lines)

        # 1. Check for exposed secrets in .env files
        if file_path.endswith('.env') or file_path.endswith('.env.local'):
            for i, line in enumerate(lines, 1):
                if '=' in line and not line.strip().startswith('#'):
                    key, value = line.split('=', 1)
                    key = key.strip()
                    value = value.strip()
                    if value and not value.startswith('$') and not value.startswith('${') and len(value) > 10:
                        # Skip common placeholders
                        if value not in ["your_api_key_here", "your_secret_here", "your_token_here", ""]:
                            add_issue(issues, file_path, i, "HIGH", f"Environment variable '{key}' appears to contain a hardcoded secret", "Use secret management and avoid committing secrets")

        # 2. Check for hardcoded secrets in code (API keys, tokens, etc.)
        secret_patterns = [
            r'["\']api_key["\']\s*[:=]\s*["\'][^"\']{10,}["\']',
            r'["\']secret["\']\s*[:=]\s*["\'][^"\']{10,}["\']',
            r'["\']token["\']\s*[:=]\s*["\'][^"\']{10,}["\']',
            r'["\']password["\']\s*[:=]\s*["\'][^"\']{10,}["\']',
            r'["\']aws_access_key_id["\']\s*[:=]\s*["\'][^"\']{10,}["\']',
            r'["\']aws_secret_access_key["\']\s*[:=]\s*["\'][^"\']{10,}["\']',
        ]
        for pattern in secret_patterns:
            for i, line in enumerate(lines, 1):
                if re.search(pattern, line, re.IGNORECASE):
                    add_issue(issues, file_path, i, "HIGH", f"Potential hardcoded secret found: {line.strip()}", "Use environment variables and avoid hardcoding secrets")

        # 3. Check for console.log (low severity)
        for i, line in enumerate(lines, 1):
            if "console.log" in line and not line.strip().startswith("//"):
                add_issue(issues, file_path, i, "LOW", f"Console.log found in code: {line.strip()}", "Remove console.log before production or use a logger that can be turned off")

        # 4. Check for missing error handling in API functions (async functions without try/catch)
        if file_path.startswith(r"src\api\\") and file_path != r"src\api\client.ts":
            # Find async functions
            for i, line in enumerate(lines, 1):
                if "async " in line and ("function" in line or "(" in line):
                    # Look ahead for the function body (simplified: check next 20 lines for try or catch)
                    found_try = False
                    found_catch = False
                    for j in range(i, min(i+20, total_lines)):
                        if "try {" in lines[j] or "try (" in lines[j]:
                            found_try = True
                        if "catch (" in lines[j] or ".catch(" in lines[j]:
                            found_catch = True
                    if not found_try and not found_catch:
                        add_issue(issues, file_path, i, "MEDIUM", f"Async function may lack error handling (try/catch or .catch)", "Wrap async logic in try/catch or ensure the returned promise is handled by the caller")

        # 5. Check for event listeners without removal (in components, pages, hooks)
        if file_path.startswith(r"src\components\\") or file_path.startswith(r"src\pages\\") or file_path.startswith(r"src\hooks\\"):
            for i, line in enumerate(lines, 1):
                if "addEventListener" in line:
                    # Check if there's a removeEventListener in the same file
                    if "removeEventListener" not in content:
                        add_issue(issues, file_path, i, "HIGH", "Event listener added but not removed (potential memory leak)", "Add cleanup to remove the event listener")
                if "setInterval" in line or "setTimeout" in line:
                    if "clearInterval" not in content and "clearTimeout" not in content:
                        add_issue(issues, file_path, i, "HIGH", "Timer set but not cleared (potential memory leak)", "Clear intervals and timeouts in cleanup")
                if "new WebSocket" in line or "WebSocket(" in line:
                    if "close()" not in content and ".close" not in content:
                        add_issue(issues, file_path, i, "HIGH", "WebSocket may not be closed (potential memory leak)", "Close WebSocket in cleanup")

        # 6. Check for useEffect without cleanup (in components, pages, hooks)
        if file_path.startswith(r"src\components\\") or file_path.startswith(r"src\pages\\") or file_path.startswith(r"src\hooks\\"):
            for i, line in enumerate(lines, 1):
                if "useEffect" in line:
                    # Look ahead for the useEffect callback and see if it returns a cleanup function
                    # This is a simplified check: we'll look for a return statement in the next 10 lines
                    found_return = False
                    for j in range(i, min(i+10, total_lines)):
                        if "return () =>" in lines[j] or "return function" in lines[j]:
                            found_return = True
                            break
                    # If we don't find a return, it might still be okay if the effect doesn't need cleanup.
                    # We'll only flag if we see subscription-like operations.
                    if not found_return:
                        # Check if the effect contains subscription-like operations
                        effect_snippet = ''.join(lines[i:i+10])
                        if "addEventListener" in effect_snippet or "subscribe" in effect_snippet or "WebSocket" in effect_snippet:
                            add_issue(issues, file_path, i, "HIGH", "useEffect may be missing cleanup for subscriptions", "Ensure the useEffect returns a cleanup function to remove subscriptions")

        # 7. Check for missing ErrorBoundary in App.tsx
        if file_path == r"src\App.tsx":
            if "<ErrorBoundary>" not in content:
                add_issue(issues, file_path, 0, "HIGH", "App component does not wrap routes in ErrorBoundary", "Wrap the main routes or the entire app in ErrorBoundary to catch unexpected errors")
            # Check for router
            if "BrowserRouter" not in content and "Router" not in content:
                add_issue(issues, file_path, 0, "HIGH", "No router found in App.tsx", "Ensure the app is wrapped in a router")

        # 8. Check for missing ErrorBoundary in main.tsx
        if file_path == r"src\main.tsx":
            if "<ErrorBoundary>" not in content:
                add_issue(issues, file_path, 0, "HIGH", "main.tsx missing ErrorBoundary", "Wrap the App component in ErrorBoundary")
            if "React.StrictMode" not in content:
                add_issue(issues, file_path, 0, "LOW", "main.tsx missing React.StrictMode", "Consider wrapping app in React.StrictMode for better development checks")

        # 9. Check tsconfig.json for strict mode
        if file_path == r"tsconfig.json":
            if '"strict": false' in content:
                add_issue(issues, file_path, 0, "MEDIUM", "TypeScript strict mode is disabled", "Enable strict mode for better type safety")

        # 10. Check vite.config.ts for proxy target (should be environment variable)
        if file_path == r"vite.config.ts":
            if "localhost:8080" in content:
                add_issue(issues, file_path, 0, "MEDIUM", "Vite proxy target is hardcoded to localhost:8080", "Use environment variable for the proxy target to allow different environments")

        # 11. Check auth.ts for token storage security
        if file_path == r"src\api\auth.ts":
            if "localStorage.setItem" in content and ("token" in content.lower() or "jwt" in content.lower()):
                add_issue(issues, file_path, 0, "MEDIUM", "Auth token stored in localStorage (vulnerable to XSS)", "Consider using httpOnly cookies or secure storage mechanisms")

        # 12. Check voice-related hooks for cleanup
        if file_path in [r"src\hooks\useSpeechRecognition.ts", r"src\hooks\useVoiceRecorder.ts", r"src\hooks\useSpeechSynthesis.ts"]:
            # Check for cleanup of media resources
            if "cancel" not in content and "stop" not in content and "close" not in content:
                add_issue(issues, file_path, 0, "HIGH", "Voice-related hook may not clean up media resources", "Ensure that recognition, recording, or synthesis is stopped and resources released")

        # 13. Check VoiceChatOverlay for cleanup
        if file_path == r"src\components\VoiceChatOverlay.tsx":
            if "useEffect" in content:
                if "return" not in content or "cleanup" not in content:
                    add_issue(issues, file_path, 0, "HIGH", "VoiceChatOverlay may not clean up voice resources", "Ensure that the voice recognition/synthesis is stopped in the cleanup function")

    # Output the issues
    print("Frontend Error Analysis Report")
    print("=" * 60)
    print(f"Total issues found: {len(issues)}")
    print()

    # Sort by severity and file
    severity_order = {"CRITICAL": 0, "HIGH": 1, "MEDIUM": 2, "LOW": 3}
    issues_sorted = sorted(issues, key=lambda x: (severity_order.get(x["severity"], 4), x["file"], x["line"]))

    for issue in issues_sorted:
        print(f"File: {issue['file']}")
        if issue["line"] > 0:
            print(f"Line: {issue['line']}")
        print(f"Severity: {issue['severity']}")
        print(f"Description: {issue['description']}")
        print(f"Fix: {issue['fix']}")
        print("-" * 60)

if __name__ == "__main__":
    main()