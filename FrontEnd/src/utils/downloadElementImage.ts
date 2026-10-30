/** 把某个内容区域导出为一张 PNG，不包含它外面的页面框架。横向滚动的内容会展开进同一张图。 */

const XHTML_NS = 'http://www.w3.org/1999/xhtml';
const SVG_NS = 'http://www.w3.org/2000/svg';

export async function downloadElementImage(root: HTMLElement, filename: string) {
  const { width, height, clone } = snapshotContent(root);
  const pad = 16;
  const totalWidth = width + pad * 2;
  const totalHeight = height + pad * 2;
  const dataUrl = await rasterize(clone, totalWidth, totalHeight, pad);
  triggerDownload(dataUrl, filename.endsWith('.png') ? filename : `${filename}.png`);
}

function snapshotContent(root: HTMLElement) {
  const host = document.createElement('div');
  host.style.cssText = `position:fixed;left:-100000px;top:0;width:${root.clientWidth}px;overflow:visible;pointer-events:none;`;
  const clone = root.cloneNode(true) as HTMLElement;
  clone.querySelectorAll<HTMLElement>('[data-export-ignore]').forEach((node) => {
    const extra = node.parentElement?.classList.contains('ant-card-extra') ? node.parentElement : null;
    node.remove();
    if (extra && extra.childElementCount === 0) extra.remove();
  });
  syncFormValues(root, clone);
  host.appendChild(clone);
  document.body.appendChild(host);
  try {
    expandScrollers(clone);
    const width = Math.max(clone.scrollWidth, clone.offsetWidth, 1);
    clone.style.width = `${width}px`;
    clone.style.maxWidth = 'none';
    const height = Math.max(clone.scrollHeight, clone.offsetHeight, 1);
    return { width, height, clone };
  } finally {
    host.remove();
  }
}

function expandScrollers(root: HTMLElement) {
  const nodes = [root, ...root.querySelectorAll<HTMLElement>('*')].reverse();
  nodes.forEach((el) => {
    const style = getComputedStyle(el);
    const wide = (style.overflowX === 'auto' || style.overflowX === 'scroll') && el.scrollWidth > el.clientWidth + 1;
    const tall = (style.overflowY === 'auto' || style.overflowY === 'scroll') && el.scrollHeight > el.clientHeight + 1;
    if (!wide && !tall) return;
    el.style.overflow = 'visible';
    el.style.maxWidth = 'none';
    el.style.maxHeight = 'none';
    if (wide) el.style.width = `${el.scrollWidth}px`;
    if (tall) el.style.height = `${el.scrollHeight}px`;
  });
}

function syncFormValues(source: HTMLElement, clone: HTMLElement) {
  const src = source.querySelectorAll('input, textarea, select');
  const dst = clone.querySelectorAll('input, textarea, select');
  src.forEach((node, index) => {
    const target = dst[index];
    if (!target) return;
    if (node instanceof HTMLInputElement && target instanceof HTMLInputElement) {
      target.value = node.value;
      target.setAttribute('value', node.value);
      if (node.type === 'checkbox' || node.type === 'radio') {
        target.checked = node.checked;
        if (node.checked) target.setAttribute('checked', 'checked');
        else target.removeAttribute('checked');
      }
      return;
    }
    if (node instanceof HTMLTextAreaElement && target instanceof HTMLTextAreaElement) {
      target.value = node.value;
      target.textContent = node.value;
      return;
    }
    if (node instanceof HTMLSelectElement && target instanceof HTMLSelectElement) {
      target.value = node.value;
      Array.from(target.options).forEach((option) => {
        if (option.value === node.value) option.setAttribute('selected', 'selected');
        else option.removeAttribute('selected');
      });
    }
  });
}

async function rasterize(clone: HTMLElement, width: number, height: number, pad: number) {
  const svg = document.createElementNS(SVG_NS, 'svg');
  svg.setAttribute('xmlns', SVG_NS);
  svg.setAttribute('width', String(width));
  svg.setAttribute('height', String(height));
  const foreign = document.createElementNS(SVG_NS, 'foreignObject');
  foreign.setAttribute('x', '0');
  foreign.setAttribute('y', '0');
  foreign.setAttribute('width', String(width));
  foreign.setAttribute('height', String(height));

  const wrapper = document.createElementNS(XHTML_NS, 'div');
  wrapper.setAttribute('xmlns', XHTML_NS);
  const bodyStyle = getComputedStyle(document.body);
  wrapper.style.boxSizing = 'border-box';
  wrapper.style.width = `${width}px`;
  wrapper.style.height = `${height}px`;
  wrapper.style.padding = `${pad}px`;
  wrapper.style.background = '#f5f6fa';
  wrapper.style.fontFamily = bodyStyle.fontFamily;
  wrapper.style.fontSize = bodyStyle.fontSize;
  wrapper.style.color = bodyStyle.color;
  copyCustomProps(document.documentElement, wrapper);
  copyCustomProps(document.body, wrapper);

  const style = document.createElementNS(XHTML_NS, 'style');
  style.textContent = collectCssText();
  wrapper.appendChild(style);
  wrapper.appendChild(clone);
  foreign.appendChild(wrapper);
  svg.appendChild(foreign);

  const xml = new XMLSerializer().serializeToString(svg);
  // blob 地址画进画布会被判定为跨域，data URL 才能导出 PNG
  const url = `data:image/svg+xml;charset=utf-8,${encodeURIComponent(xml)}`;
  const image = await loadImage(url);
  const scale = pickScale(width, height);
  const canvas = document.createElement('canvas');
  canvas.width = Math.ceil(width * scale);
  canvas.height = Math.ceil(height * scale);
  const ctx = canvas.getContext('2d');
  if (!ctx) throw new Error('canvas');
  ctx.scale(scale, scale);
  ctx.fillStyle = '#f5f6fa';
  ctx.fillRect(0, 0, width, height);
  ctx.drawImage(image, 0, 0, width, height);
  return canvas.toDataURL('image/png');
}

function pickScale(width: number, height: number) {
  const maxSide = 16000;
  const maxArea = 24_000_000;
  let scale = 2;
  if (width * scale > maxSide || height * scale > maxSide) {
    scale = Math.min(maxSide / width, maxSide / height);
  }
  if (width * height * scale * scale > maxArea) {
    scale = Math.sqrt(maxArea / (width * height));
  }
  return Math.max(1, scale);
}

function copyCustomProps(from: Element, to: HTMLElement) {
  const computed = getComputedStyle(from);
  for (let i = 0; i < computed.length; i += 1) {
    const prop = computed.item(i);
    if (prop.startsWith('--')) {
      to.style.setProperty(prop, computed.getPropertyValue(prop));
    }
  }
}

function collectCssText() {
  const chunks: string[] = [];
  for (const sheet of Array.from(document.styleSheets)) {
    let rules: CSSRuleList;
    try {
      rules = sheet.cssRules;
    } catch {
      continue;
    }
    chunks.push(Array.from(rules, (rule) => rule.cssText).join('\n'));
  }
  return sanitizeCssUrls(chunks.join('\n'));
}

/** 外链图片/字体会污染画布，导出时改成空图。 */
function sanitizeCssUrls(css: string) {
  return css.replace(/url\(([^)]*)\)/g, (full, inner: string) => {
    const value = inner.trim().replace(/^['"]|['"]$/g, '');
    if (value.startsWith('data:')) return full;
    return 'url("data:image/gif;base64,R0lGODlhAQABAIAAAAAAAP///yH5BAEAAAAALAAAAAABAAEAAAIBRAA7")';
  });
}

function loadImage(url: string) {
  return new Promise<HTMLImageElement>((resolve, reject) => {
    const image = new Image();
    image.onload = () => resolve(image);
    image.onerror = () => reject(new Error('image'));
    image.src = url;
  });
}

function triggerDownload(href: string, filename: string) {
  const a = document.createElement('a');
  a.href = href;
  a.download = filename;
  document.body.appendChild(a);
  a.click();
  document.body.removeChild(a);
}
