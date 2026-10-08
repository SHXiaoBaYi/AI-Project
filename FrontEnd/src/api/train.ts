import request from '@/api/request';
import { getToken } from '@/utils/auth';
import { withBase } from '@/utils/basePath';
import { resolveUploadUrl } from '@/utils/uploadUrl';

export type TrainDoc = {
  id: number;
  title: string;
  category?: string;
  description?: string;
  latestVersionId?: number;
  latestVersionNo?: number;
  latestVersionLabel?: string;
  latestFileName?: string;
  /** /uploads/... */
  latestFilePath?: string;
  /** 公网标准下载地址 */
  latestFileUrl?: string;
  versionCount?: number;
  status?: number;
  updateTime?: string;
};

export type TrainDocVersion = {
  id: number;
  docId: number;
  versionNo: number;
  versionLabel?: string;
  fileName?: string;
  filePath?: string;
  fileUrl?: string;
  fileSize?: number;
  remark?: string;
  latest?: boolean;
  createTime?: string;
  createBy?: string;
};

export type TrainDocPreview = {
  id: number;
  docId: number;
  title?: string;
  versionNo?: number;
  versionLabel?: string;
  fileName?: string;
  filePath?: string;
  /** 公网可访问地址，供 Office Online 拉取 */
  fileUrl?: string;
  /** office | pdf | html | docx */
  renderMode?: string;
  /** 服务器上原文件是否存在 */
  fileReady?: boolean;
  fileMissing?: boolean;
  html?: string;
  text?: string;
};

export type TrainAssistantMeta = {
  docTitle?: string;
  latestVersionLabel?: string;
  latestVersionNo?: number;
  hasDocument?: boolean;
  hasOlderVersions?: boolean;
  category?: string;
};

export type TrainQaItem = {
  question?: string;
  answer?: string;
  /** 含配图的 HTML，优先展示 */
  answerHtml?: string;
  /** 相关配图 /uploads/... */
  images?: string[];
  versionLabel?: string;
  calibrateId?: number;
  fromCalibrate?: boolean;
};

export type TrainAssistantAskResult = {
  status: 'HIT' | 'NEED_OLD' | 'NOT_FOUND' | string;
  message?: string;
  items?: TrainQaItem[];
  versionLabel?: string;
  hasOlderVersions?: boolean;
  docId?: number;
  versionId?: number;
};

export type TrainQaCalibrate = {
  id: number;
  docId?: number;
  docTitle?: string;
  versionId?: number;
  versionLabel?: string;
  latestVersion?: boolean;
  category?: string;
  userQuestion?: string;
  aliases?: string;
  topicTitle?: string;
  answer?: string;
  answerHtml?: string;
  images?: string[];
  status?: string;
  hitCount?: number;
  source?: string;
  remark?: string;
  updateBy?: string;
  updateTime?: string;
};

export type TrainQaCalibratePage = {
  total: number;
  rows: TrainQaCalibrate[];
};

export function listTrainDocsApi(category = 'cashier') {
  return request.get<unknown, TrainDoc[]>('/train/doc/list', { params: { category } });
}

export function saveTrainDocApi(data: { id?: number; title: string; category?: string; description?: string }) {
  return data.id ? request.put<unknown, number>('/train/doc', data) : request.post<unknown, number>('/train/doc', data);
}

export function deleteTrainDocApi(id: number) {
  return request.delete(`/train/doc/${id}`);
}

export function listTrainVersionsApi(docId: number) {
  return request.get<unknown, TrainDocVersion[]>(`/train/doc/${docId}/versions`);
}

export function createTrainDocWithFileApi(data: {
  file: File;
  title: string;
  description?: string;
  versionLabel?: string;
  remark?: string;
}) {
  const form = new FormData();
  form.append('file', data.file);
  form.append('title', data.title);
  form.append('category', 'cashier');
  if (data.description) form.append('description', data.description);
  if (data.versionLabel) form.append('versionLabel', data.versionLabel);
  if (data.remark) form.append('remark', data.remark);
  return request.post<unknown, number>('/train/doc/upload', form, {
    headers: { 'Content-Type': 'multipart/form-data' },
  });
}

export function uploadTrainVersionApi(docId: number, file: File, versionLabel?: string, remark?: string) {
  const form = new FormData();
  form.append('file', file);
  if (versionLabel) form.append('versionLabel', versionLabel);
  if (remark) form.append('remark', remark);
  return request.post<unknown, number>(`/train/doc/${docId}/version`, form, {
    headers: { 'Content-Type': 'multipart/form-data' },
  });
}

export function pickUploadFile(fileList: unknown): File | undefined {
  const list = Array.isArray(fileList) ? fileList : [];
  const first = list[0] as { originFileObj?: File; raw?: File } | File | undefined;
  if (!first) return undefined;
  if (first instanceof File) return first;
  return first.originFileObj || first.raw;
}

export function previewTrainVersionApi(versionId: number) {
  return request.get<unknown, TrainDocPreview>(`/train/doc/version/${versionId}/preview`);
}

/** Microsoft Office Online 嵌入预览（需公网可访问的文件 URL） */
export function buildOfficeOnlineEmbedUrl(fileUrl: string) {
  const src = String(fileUrl || '').trim();
  if (!src) return '';
  return `https://view.officeapps.live.com/op/embed.aspx?src=${encodeURIComponent(src)}`;
}

/** 拉取原文件二进制，供 docx-preview / PDF 预览 */
export async function fetchTrainVersionFileApi(versionId: number) {
  const token = getToken();
  const res = await fetch(withBase(`/api/train/doc/version/${versionId}/file`), {
    headers: { Authorization: `Bearer ${token}` },
    redirect: 'follow',
  });
  const contentType = res.headers.get('content-type') || '';
  const buffer = await res.arrayBuffer();
  const looksJson =
    contentType.includes('application/json') ||
    (buffer.byteLength > 0 && buffer.byteLength < 4096 && new TextDecoder().decode(buffer.slice(0, 1)) === '{');
  if (!res.ok || looksJson) {
    let msg = '文件打不开';
    try {
      const body = JSON.parse(new TextDecoder().decode(buffer)) as { msg?: string };
      if (body?.msg) msg = body.msg;
    } catch {
      if (!res.ok) msg = `文件打不开（${res.status}）`;
    }
    throw new Error(msg);
  }
  return { blob: new Blob([buffer], { type: contentType || undefined }), contentType };
}

/** 有 fileUrl 直接 window.open；否则回退鉴权下载接口。 */
export async function downloadTrainVersionApi(versionId: number, fileName?: string, filePathOrUrl?: string) {
  const raw = (filePathOrUrl || '').trim();
  if (raw) {
    const url = /^https?:\/\//i.test(raw)
      ? raw
      : resolveUploadUrl(
          raw.startsWith('/uploads/') ? raw : raw.includes('/uploads/') ? raw.slice(raw.indexOf('/uploads/')) : '',
        );
    if (url && url !== '#') {
      window.open(url, '_blank', 'noopener,noreferrer');
      return;
    }
  }

  const { blob } = await fetchTrainVersionFileApi(versionId);
  const objectUrl = URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = objectUrl;
  a.download = fileName || 'train.doc';
  a.click();
  URL.revokeObjectURL(objectUrl);
}

export type TrainAssistantEntry = {
  pageUrl?: string;
  dingTalkUrl?: string;
  corpId?: string;
  ready?: boolean;
  message?: string;
};

export type TrainJsapiConfig = {
  agentId: string;
  corpId: string;
  timeStamp: string;
  nonceStr: string;
  signature: string;
};

export function getTrainAssistantEntryApi() {
  return request.get<unknown, TrainAssistantEntry>('/train/assistant/entry');
}

export function getTrainJsapiConfigApi(url: string) {
  return request.get<unknown, TrainJsapiConfig>('/train/assistant/jsapi-config', { params: { url } });
}

export function getTrainAssistantMetaApi(category = 'cashier') {
  return request.get<unknown, TrainAssistantMeta>('/train/assistant/meta', { params: { category } });
}

export function askTrainAssistantApi(data: { question: string; searchOld?: boolean; category?: string }) {
  return request.post<unknown, TrainAssistantAskResult>('/train/assistant/ask', {
    category: 'cashier',
    ...data,
  });
}

export function feedbackTrainAssistantApi(data: {
  userQuestion: string;
  accurate: boolean;
  category?: string;
  docId?: number;
  versionId?: number;
  calibrateId?: number;
  topicTitle?: string;
  answer?: string;
  answerHtml?: string;
  images?: string[];
}) {
  return request.post<unknown, number>('/train/assistant/feedback', {
    category: 'cashier',
    ...data,
  });
}

export function listTrainQaCalibrateApi(params: {
  pageNum?: number;
  pageSize?: number;
  category?: string;
  docId?: number;
  status?: string;
  keyword?: string;
  onlyLatest?: boolean;
}) {
  return request.get<unknown, TrainQaCalibratePage>('/train/qa/list', {
    params: { category: 'cashier', ...params },
  });
}

export function saveTrainQaCalibrateApi(data: {
  id?: number;
  docId?: number;
  versionId?: number;
  category?: string;
  userQuestion: string;
  aliases?: string;
  topicTitle?: string;
  answer?: string;
  answerHtml?: string;
  images?: string[];
  status?: string;
  remark?: string;
}) {
  return request.post<unknown, number>('/train/qa', { category: 'cashier', ...data });
}

export function deleteTrainQaCalibrateApi(id: number) {
  return request.delete(`/train/qa/${id}`);
}

export function rerecognizeTrainQaCalibrateApi(id: number) {
  return request.post<unknown, TrainQaCalibrate>(`/train/qa/${id}/rerecognize`);
}
