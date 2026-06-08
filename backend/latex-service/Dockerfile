FROM continuumio/miniconda3:latest

# Install Tectonic via conda-forge (most reliable method)
RUN conda install -c conda-forge tectonic -y \
    && conda clean -afy

# Install pip and FastAPI
RUN pip install fastapi uvicorn python-multipart

# Set working directory
WORKDIR /app

# Copy application
COPY main.py .

# Expose port
EXPOSE 8090

# Start server
CMD ["uvicorn", "main:app", "--host", "0.0.0.0", "--port", "8090"]