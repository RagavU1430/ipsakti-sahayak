import os
import time
import logging
import httpx
from typing import Any, Dict, List, Optional

logger = logging.getLogger(__name__)

# Class-level circuit breaker: track models in cooldown after 429/503 errors
_MODEL_COOLDOWNS: Dict[str, float] = {}
COOLDOWN_SECONDS = 60.0


class OpenRouterClient:
    def __init__(self, api_key: Optional[str] = None, base_url: Optional[str] = None):
        from app.core.config import load_env
        load_env()
        self.api_key = api_key or os.getenv("OPENROUTER_API_KEY")
        if not self.api_key:
            raise ValueError("OPENROUTER_API_KEY environment variable is not set")
        self.base_url = base_url or os.getenv("OPENROUTER_BASE_URL", "https://openrouter.ai/api/v1").rstrip("/")
        self.headers = {
            "Authorization": f"Bearer {self.api_key}",
            "Content-Type": "application/json",
            "HTTP-Referer": "https://github.com/RagavU1430/ipsakti-sahayak", # Required by OpenRouter
            "X-Title": "IP Sakthi A RAG"
        }
        # Persistent HTTP client with connection pooling and keep-alive
        self._client = httpx.Client(
            limits=httpx.Limits(max_keepalive_connections=20, max_connections=50, keepalive_expiry=60.0),
            timeout=httpx.Timeout(connect=3.0, read=10.0, write=5.0, pool=5.0)
        )

    def close(self):
        try:
            self._client.close()
        except Exception:
            pass

    def embed(self, texts: List[str], model: str = "openai/text-embedding-3-small") -> List[List[float]]:
        """
        Generate embeddings for a list of texts using OpenRouter's embeddings API.
        """
        url = f"{self.base_url}/embeddings"
        payload = {
            "model": model,
            "input": texts
        }
        response = self._client.post(url, headers=self.headers, json=payload, timeout=30.0)
        response.raise_for_status()
        data = response.json()
        embeddings = [item["embedding"] for item in data["data"]]
        return embeddings

    def chat_complete(
        self, 
        messages: List[Dict[str, str]], 
        model: str = "openrouter/free", 
        temperature: float = 0.3,
        max_tokens: Optional[int] = None,
        timeout: float = 6.0
    ) -> Dict[str, Any]:
        """
        Query OpenRouter chat completion API, trying each configured model until one responds quickly.
        Skips models currently under cooldown due to previous rate limits (HTTP 429).
        """
        url = f"{self.base_url}/chat/completions"
        if isinstance(model, str):
            models_list = [m.strip() for m in model.split(",") if m.strip()]
        else:
            models_list = list(model)
        
        if not models_list:
            models_list = ["openrouter/free"]

        now = time.time()
        # Filter out models in cooldown if we have other models available
        active_models = [m for m in models_list if _MODEL_COOLDOWNS.get(m, 0.0) <= now]
        if not active_models:
            # All models in cooldown, try the one whose cooldown expires earliest
            earliest = min(models_list, key=lambda m: _MODEL_COOLDOWNS.get(m, 0.0))
            active_models = [earliest]

        last_err = None
        for current_model in active_models:
            payload: Dict[str, Any] = {
                "model": current_model,
                "messages": messages,
                "temperature": temperature
            }
            if max_tokens:
                payload["max_tokens"] = max_tokens

            try:
                response = self._client.post(url, headers=self.headers, json=payload, timeout=timeout)
                if response.status_code in (429, 503):
                    _MODEL_COOLDOWNS[current_model] = time.time() + COOLDOWN_SECONDS
                    logger.warning("Model %s returned HTTP %s; cooling down for %ss", current_model, response.status_code, COOLDOWN_SECONDS)
                    continue

                response.raise_for_status()
                data = response.json()
                if "choices" in data and len(data["choices"]) > 0:
                    content = data["choices"][0].get("message", {}).get("content")
                    if content:
                        return data
            except httpx.HTTPStatusError as e:
                if e.response.status_code in (429, 503):
                    _MODEL_COOLDOWNS[current_model] = time.time() + COOLDOWN_SECONDS
                last_err = e
                logger.warning("Model %s failed with status %s: %s", current_model, e.response.status_code, e)
                continue
            except Exception as e:
                last_err = e
                logger.warning("Model %s error or timeout after %ss: %s", current_model, timeout, e)
                continue
        
        if last_err:
            raise last_err
        raise RuntimeError("No configured model responded successfully.")


