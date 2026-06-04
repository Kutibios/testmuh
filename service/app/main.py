"""Deployment Tracker mini-servisi.

CI/CD pipeline'larinin deployment kayitlarini tuttugu kucuk bir REST servis.
Yazilim Test Muhendisligi odevi icin Rest Assured testlerinin hedef sistemidir.
"""
from __future__ import annotations

import uuid
from datetime import datetime, timezone
from typing import List, Literal

from fastapi import FastAPI, HTTPException, Response, status
from pydantic import BaseModel, Field

from .store import store

app = FastAPI(
    title="Deployment Tracker",
    description="Mini CI/CD deployment kayit servisi (Yazilim Test Muhendisligi odevi)",
    version="1.0.0",
)


class DeploymentIn(BaseModel):
    service: str = Field(..., min_length=1, examples=["payment-api"])
    version: str = Field(..., min_length=1, examples=["v1.4.2"])
    environment: Literal["dev", "staging", "prod"]
    status: Literal["success", "failed", "in_progress"] = "in_progress"


class Deployment(DeploymentIn):
    id: str
    created_at: str


@app.get("/health")
def health() -> dict:
    return {"status": "ok"}


@app.get("/deployments", response_model=List[Deployment])
def list_deployments() -> List[dict]:
    return store.list_all()


@app.get("/deployments/{deployment_id}", response_model=Deployment)
def get_deployment(deployment_id: str) -> dict:
    found = store.get(deployment_id)
    if found is None:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail=f"Deployment bulunamadi: {deployment_id}",
        )
    return found


@app.post(
    "/deployments",
    response_model=Deployment,
    status_code=status.HTTP_201_CREATED,
)
def create_deployment(payload: DeploymentIn) -> dict:
    record = {
        **payload.model_dump(),
        "id": str(uuid.uuid4()),
        "created_at": datetime.now(timezone.utc).isoformat(),
    }
    store.add(record)
    return record


@app.delete("/deployments/{deployment_id}", status_code=status.HTTP_204_NO_CONTENT)
def delete_deployment(deployment_id: str) -> Response:
    if not store.delete(deployment_id):
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail=f"Deployment bulunamadi: {deployment_id}",
        )
    return Response(status_code=status.HTTP_204_NO_CONTENT)
