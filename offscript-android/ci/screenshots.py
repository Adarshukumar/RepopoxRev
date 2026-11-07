"""Small native emulator thumbnails via GitHub API, accessible to the restricted review sandbox."""
import base64, io, os, subprocess
from pathlib import Path
from PIL import Image
for path in sorted(Path('screenshots').glob('*.png')):
    image=Image.open(path).convert('RGB')
    image.thumbnail((380,850))
    buffer=io.BytesIO()
    image.save(buffer,format='JPEG',quality=65,optimize=True)
    payload=base64.b64encode(buffer.getvalue()).decode()
    if len(payload)>60000:
        continue
    Path('screenshot-comment.md').write_text(f'Native emulator screenshot: {path.name}\n\n```base64-jpeg\n{payload}\n```')
    subprocess.run(['gh','api','-X','POST',f'repos/{os.environ["GITHUB_REPOSITORY"]}/commits/{os.environ["GITHUB_SHA"]}/comments','-F','body=@screenshot-comment.md'],check=True,stdout=subprocess.DEVNULL)
