import os
import httpx
from typing import Any, Dict, List, Optional

class OpenRouterClient:
    def __init__(self, api_key: Optional[str] = None):
        from app.core.config import load_env
        load_env()
        self.api_key = api_key or os.getenv("OPENROUTER_API_KEY")
        if not self.api_key:
            raise ValueError("OPENROUTER_API_KEY environment variable is not set")
        self.base_url = "https://openrouter.ai/api/v1"
        self.headers = {
            "Authorization": f"Bearer {self.api_key}",
            "Content-Type": "application/json",
            "HTTP-Referer": "https://github.com/RagavU1430/ipsakti-sahayak", # Required by OpenRouter
            "X-Title": "IP Sakthi A RAG"
        }

    def embed(self, texts: List[str], model: str = "openai/text-embedding-3-small") -> List[List[float]]:
        """
        Generate embeddings for a list of texts using OpenRouter's embeddings API.
        """
        url = f"{self.base_url}/embeddings"
        payload = {
            "model": model,
            "input": texts
        }
        
        with httpx.Client(timeout=60.0) as client:
            response = client.post(url, headers=self.headers, json=payload)
            response.raise_for_status()
            data = response.json()
            # The standard OpenAI embeddings format: {"data": [{"embedding": [...]}, ...]}
            embeddings = [item["embedding"] for item in data["data"]]
            return embeddings

    def chat_complete(
        self, 
        messages: List[Dict[str, str]], 
        model: str = "openrouter/free", 
        temperature: float = 0.3,
        max_tokens: Optional[int] = None
    ) -> Dict[str, Any]:
        """
        Query OpenRouter chat completion API.
        """
        url = f"{self.base_url}/chat/completions"
        payload = {
            "model": model,
            "messages": messages,
            "temperature": temperature
        }
        if max_tokens:
            payload["max_tokens"] = max_tokens

        with httpx.Client(timeout=90.0) as client:
            response = client.post(url, headers=self.headers, json=payload)
            response.raise_for_status()
            return response.json()
