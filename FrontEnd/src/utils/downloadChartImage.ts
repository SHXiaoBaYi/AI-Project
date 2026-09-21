/** 把看板图表区域导出为 PNG。优先用 canvas，否则尝试 SVG → canvas。 */
export function downloadChartImage(root: HTMLElement | null, filename: string) {
  if (!root) return;
  const canvas = root.querySelector('canvas');
  if (canvas) {
    triggerDownload(canvas.toDataURL('image/png'), filename.endsWith('.png') ? filename : `${filename}.png`);
    return;
  }
  const svg = root.querySelector('svg');
  if (!svg) return;
  const cloned = svg.cloneNode(true) as SVGElement;
  const box = svg.getBoundingClientRect();
  const width = Math.max(1, Math.ceil(box.width || Number(svg.getAttribute('width')) || 800));
  const height = Math.max(1, Math.ceil(box.height || Number(svg.getAttribute('height')) || 400));
  cloned.setAttribute('xmlns', 'http://www.w3.org/2000/svg');
  cloned.setAttribute('width', String(width));
  cloned.setAttribute('height', String(height));
  const xml = new XMLSerializer().serializeToString(cloned);
  const image = new Image();
  const url = URL.createObjectURL(new Blob([xml], { type: 'image/svg+xml;charset=utf-8' }));
  image.onload = () => {
    const out = document.createElement('canvas');
    out.width = width;
    out.height = height;
    const ctx = out.getContext('2d');
    if (ctx) {
      ctx.fillStyle = '#ffffff';
      ctx.fillRect(0, 0, width, height);
      ctx.drawImage(image, 0, 0, width, height);
      triggerDownload(out.toDataURL('image/png'), filename.endsWith('.png') ? filename : `${filename}.png`);
    }
    URL.revokeObjectURL(url);
  };
  image.onerror = () => URL.revokeObjectURL(url);
  image.src = url;
}

function triggerDownload(href: string, filename: string) {
  const a = document.createElement('a');
  a.href = href;
  a.download = filename;
  document.body.appendChild(a);
  a.click();
  document.body.removeChild(a);
}
