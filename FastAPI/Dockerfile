FROM python:3.11-slim

WORKDIR /app

ENV PYTHONDONTWRITEBYTECODE=1
ENV PYTHONUNBUFFERED=1

COPY requirements.txt ./
RUN pip install --no-cache-dir -r requirements.txt

RUN useradd --create-home --uid 1001 appuser \
    && mkdir -p /app/uploads \
    && chown appuser:appuser /app/uploads

COPY --chown=appuser:appuser . .

USER appuser

EXPOSE 8086

CMD ["python", "-m", "uvicorn", "main:app", "--host", "0.0.0.0", "--port", "8086"]