FROM python:3.11-slim

WORKDIR /app

COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

COPY . .

ENV HOST=0.0.0.0
EXPOSE 7860

# supervisor auto-restarts the server if it ever crashes
CMD ["python3", "run.py", "--port", "7860"]
