import sys
import time
import uvicorn

if __name__ == "__main__":
    print("[RAG Service] Starting robust runner on port 8000...")
    while True:
        try:
            config = uvicorn.Config(
                "app.api.main:app",
                host="0.0.0.0",
                port=8000,
                log_level="info",
                access_log=True,
            )
            server = uvicorn.Server(config)
            # Prevent signal/console handlers from terminating server on background pipe events
            server.install_signal_handlers = lambda: None
            server.run()
        except BaseException as e:
            print(f"[RAG Service] Auto-recovering after: {e}", file=sys.stderr)
        time.sleep(1)
