from fastapi import FastAPI, HTTPException
from fastapi.responses import Response
from pydantic import BaseModel
import subprocess
import tempfile
import os
import logging

# Setup logging
logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

app = FastAPI(title="LaTeX Compilation Service")

class LatexRequest(BaseModel):
    latex_content: str
    filename: str = "document"

@app.get("/health")
def health_check():
    """Health check endpoint"""
    return {"status": "healthy"}

@app.post("/compile")
def compile_latex(request: LatexRequest):
    """
    Receives LaTeX source code
    Compiles it with Tectonic
    Returns PDF bytes
    """
    logger.info(f"Compiling LaTeX document: {request.filename}")

    # Create a temporary directory for compilation
    # All LaTeX compilation files go here
    # Automatically deleted when done
    with tempfile.TemporaryDirectory() as tmpdir:
        # Write LaTeX content to a .tex file
        tex_file = os.path.join(tmpdir, f"{request.filename}.tex")
        with open(tex_file, 'w', encoding='utf-8') as f:
            f.write(request.latex_content)

        try:
            # Run Tectonic to compile the .tex file
            # --outdir = where to put the output PDF
            # --keep-logs = keep log files for debugging
            result = subprocess.run(
                [
                    "tectonic",
                    tex_file,
                    "--outdir", tmpdir,
                    "--keep-logs"
                ],
                capture_output=True,
                text=True,
                timeout=60  # 60 second timeout
            )

            # Check if compilation succeeded
            if result.returncode != 0:
                logger.error(f"Tectonic error: {result.stderr}")
                raise HTTPException(
                    status_code=500,
                    detail=f"LaTeX compilation failed: {result.stderr}"
                )

            # Read the compiled PDF
            pdf_file = os.path.join(
                tmpdir, f"{request.filename}.pdf"
            )

            if not os.path.exists(pdf_file):
                raise HTTPException(
                    status_code=500,
                    detail="PDF file was not generated"
                )

            with open(pdf_file, 'rb') as f:
                pdf_bytes = f.read()

            logger.info(
                f"Successfully compiled PDF: "
                f"{len(pdf_bytes)} bytes"
            )

            # Return PDF as binary response
            return Response(
                content=pdf_bytes,
                media_type="application/pdf",
                headers={
                    "Content-Disposition":
                        f"attachment; filename={request.filename}.pdf"
                }
            )

        except subprocess.TimeoutExpired:
            logger.error("Tectonic compilation timed out")
            raise HTTPException(
                status_code=500,
                detail="LaTeX compilation timed out"
            )
        except HTTPException:
            raise
        except Exception as e:
            logger.error(f"Unexpected error: {str(e)}")
            raise HTTPException(
                status_code=500,
                detail=f"Compilation error: {str(e)}"
            )