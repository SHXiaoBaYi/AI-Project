import { useState } from 'react';
import { Alert, Button, App, Table, Upload } from 'antd';
import { InboxOutlined, DownloadOutlined } from '@ant-design/icons';
import BaseModalForm from '@/components/BaseModalForm/index';
import { importUsersApi, downloadTemplateApi } from '@/api/user';
import type { UserImportResult } from '@/types/user';

interface ImportUserModalProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  onSuccess: () => void;
}

export default function ImportUserModal({ open, onOpenChange, onSuccess }: ImportUserModalProps) {
  const { message } = App.useApp();
  const [file, setFile] = useState<File | null>(null);
  const [importing, setImporting] = useState(false);
  const [result, setResult] = useState<UserImportResult | null>(null);

  const handleImport = async () => {
    if (!file) return;
    setImporting(true);
    try {
      const res = await importUsersApi(file);
      setResult(res);
      if (res.failureCount === 0) {
        message.success(`导入完成：成功 ${res.successCount} 条`);
      } else {
        message.warning(`导入完成：成功 ${res.successCount} 条，失败 ${res.failureCount} 条`);
      }
      onSuccess();
    } catch {
      message.error('导入失败，请检查文件格式');
    } finally {
      setImporting(false);
    }
  };

  const reset = () => {
    setFile(null);
    setResult(null);
  };

  return (
    <BaseModalForm
      title='批量导入用户'
      width={640}
      open={open}
      onOpenChange={(v) => {
        if (!importing) {
          if (!v) reset();
          onOpenChange(v);
        }
      }}
      submitter={false}
      modalProps={{ destroyOnHidden: true, mask: { closable: false }, styles: { body: { padding: '24px' } } }}
    >
      {/* 模板下载 */}
      <div className='mb-4 flex items-center gap-3'>
        <Button
          icon={<DownloadOutlined />}
          onClick={() => downloadTemplateApi()}
        >
          下载导入模板
        </Button>
        <span className='text-sm text-gray-400'>请先下载模板，按格式填写后上传</span>
      </div>

      {/* 上传区域 */}
      <div className='[&_.ant-upload-list-item]:p-[16px_10px]'>
        <Upload.Dragger
          accept='.xlsx,.xls'
          maxCount={1}
          fileList={file ? [{ uid: 'file', name: file.name } as any] : []}
          beforeUpload={(f) => {
            setFile(f);
            setResult(null);
            return false;
          }}
          onRemove={() => {
            setFile(null);
            setResult(null);
          }}
        >
          <p className='ant-upload-drag-icon'>
            <InboxOutlined />
          </p>
          <p className='ant-upload-text'>点击或拖拽 Excel 文件到此区域上传</p>
          <p className='ant-upload-hint'>支持 .xlsx / .xls 格式，单次最多导入一个文件</p>
        </Upload.Dragger>
      </div>

      {/* 操作按钮 */}
      <div className='mt-5 flex justify-end'>
        <Button
          type='primary'
          loading={importing}
          disabled={!file}
          onClick={handleImport}
        >
          开始导入
        </Button>
      </div>

      {/* 导入结果 */}
      {result && (
        <div className='mt-5'>
          <Alert
            type={result.failureCount === 0 ? 'success' : 'warning'}
            showIcon
            title={
              `总计 ${result.totalCount} 条，成功 ${result.successCount} 条` +
              (result.failureCount > 0 ? `，失败 ${result.failureCount} 条` : '')
            }
          />
          {result.errors.length > 0 && (
            <Table
              rowKey='rowIndex'
              size='small'
              className='mt-3'
              dataSource={result.errors}
              columns={[
                { title: '行号', dataIndex: 'rowIndex', width: 80 },
                { title: '字段', dataIndex: 'field', width: 100 },
                { title: '错误信息', dataIndex: 'message' },
              ]}
              pagination={false}
              scroll={{ y: 200 }}
            />
          )}
        </div>
      )}
    </BaseModalForm>
  );
}
