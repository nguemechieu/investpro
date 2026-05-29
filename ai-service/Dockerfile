FROM python:3.11-slim

WORKDIR /app

COPY requirements.txt /app/requirements.txt
RUN pip install --no-cache-dir -r /app/requirements.txt

COPY . /app

EXPOSE 8010

ENV AI_LOCAL_HOST=127.0.0.1
ENV AI_LOCAL_PORT=8010

CMD ["python", "-m", "grpc_tools.protoc", "-I", "proto", "--python_out=app/generated", "--grpc_python_out=app/generated", "proto/investpro_ai.proto"] && python app/server.py
