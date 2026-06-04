"""Basit in-memory deployment veri ambari.

Surec icinde calisir, restart edildiginde sifirlanir. Sunum baglaminda yeterli;
gercek bir CI/CD platformunda yerine kalici bir veritabani (Postgres, etc.) gelir.
"""
from __future__ import annotations

from typing import Dict, List, Optional


class DeploymentStore:
    def __init__(self) -> None:
        self._items: Dict[str, dict] = {}

    def add(self, deployment: dict) -> None:
        self._items[deployment["id"]] = deployment

    def get(self, deployment_id: str) -> Optional[dict]:
        return self._items.get(deployment_id)

    def list_all(self) -> List[dict]:
        return list(self._items.values())

    def delete(self, deployment_id: str) -> bool:
        return self._items.pop(deployment_id, None) is not None

    def clear(self) -> None:
        self._items.clear()


store = DeploymentStore()
