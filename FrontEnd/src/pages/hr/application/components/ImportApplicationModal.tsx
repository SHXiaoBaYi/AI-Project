import { useEffect, useState } from 'react';
import { Alert, App, Button, Progress, Table, Upload } from 'antd';
import { DownloadOutlined, InboxOutlined } from '@ant-design/icons';
import BaseModalForm from '@/components/BaseModalForm/index';
import { downloadHrApplicationTemplateApi, importHrApplicationsApi, type HrApplicationImportResult } from '@/api/hr';

const RULES = [
  { field: '姓名', required: '是', rule: '候选人姓名' },
  { field: '电话', required: '否', rule: '手机号' },
  { field: '邮箱', required: '否', rule: '需要包含 @' },
  {
    field: '岗位',
    required: '是',
    rule: '必须和招聘需求里的岗位名称完全一致。空岗位、示例岗位、系统里没有的岗位都会导致整次失败',
  },
  { field: '渠道', required: '否', rule: '填渠道名或渠道码，如 BOSS、猎聘、猎头' },
  { field: '阶段', required: '是', rule: '填阶段名或阶段码，如 已录入、初筛通过' },
  { field: '提交人', required: '否', rule: '必须是系统用户的姓名或登录账号，不能随便写' },
  { field: '投递日期', required: '是', rule: '格式 yyyy-MM-dd' },
];

export default function ImportApplicationModal({
  open,
  onOpenChange,
  onSuccess,
}: {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  onSuccess: () => void;
}) {
  const { message } = App.useApp();
  const [file, setFile] = useState<File | null>(null);
  const [importing, setImporting] = useState(false);
  const [percent, setPercent] = useState(0);
  const [result, setResult] = useState<HrApplicationImportResult | null>(null);

  useEffect(() => {
    if (!importing || percent >= 90) return;
    const timer = window.setInterval(() => {
      setPercent((current) => (current >= 90 ? current : Math.min(90, current + 4)));
    }, 200);
    return () => window.clearInterval(timer);
  }, [importing, percent]);

  const reset = () => {
    setFile(null);
    setImporting(false);
    setPercent(0);
    setResult(null);
  };

  const handleImport = async () => {
    if (!file) return;
    setImporting(true);
    setPercent(8);
    setResult(null);
    try {
      const res = await importHrApplicationsApi(file, setPercent);
      if (res.success) {
        setPercent(100);
        message.success(`已导入 ${res.successCount} 条`);
        onSuccess();
        onOpenChange(false);
        reset();
        return;
      }
      setPercent(100);
      setResult(res);
      message.error('导入失败，整次都没有写入');
    } catch {
      setResult({ success: false, total: 0, successCount: 0, errors: [{ rowIndex: 0, message: '导入没有完成' }] });
    } finally {
      setImporting(false);
    }
  };

  return (
    <BaseModalForm
      title='导入候选人'
      width={760}
      open={open}
      onOpenChange={(next) => {
        if (importing) return;
        if (!next) reset();
        onOpenChange(next);
      }}
      submitter={false}
      modalProps={{ destroyOnHidden: true, mask: { closable: false }, styles: { body: { padding: '24px' } } }}
    >
      <div className='mb-4 flex items-center gap-3'>
        <Button
          icon={<DownloadOutlined />}
          onClick={() => downloadHrApplicationTemplateApi().catch(() => message.error('模板下载失败'))}
        >
          下载导入模板
        </Button>
        <span className='text-sm text-black/45'>模板含填写说明。任意一行有错，整次导入都不会写入。</span>
      </div>
      <Table
        size='small'
        pagination={false}
        rowKey='field'
        className='mb-4'
        dataSource={RULES}
        columns={[
          { title: '字段', dataIndex: 'field', width: 100 },
          { title: '必填', dataIndex: 'required', width: 70 },
          { title: '规则', dataIndex: 'rule' },
        ]}
      />
      <Upload.Dragger
        accept='.xlsx,.xls'
        maxCount={1}
        disabled={importing}
        fileList={file ? [{ uid: 'file', name: file.name }] : []}
        beforeUpload={(next) => {
          setFile(next);
          setResult(null);
          setPercent(0);
          return false;
        }}
        onRemove={() => {
          setFile(null);
          setResult(null);
          setPercent(0);
        }}
      >
        <p className='ant-upload-drag-icon'>
          <InboxOutlined />
        </p>
        <p className='ant-upload-text'>点击或拖拽 Excel 到这里</p>
      </Upload.Dragger>
      {importing || percent > 0 ? (
        <Progress
          className='mt-4'
          percent={percent}
          status={result && !result.success ? 'exception' : percent === 100 ? 'success' : 'active'}
        />
      ) : null}
      <div className='mt-4 flex justify-end'>
        <Button
          type='primary'
          loading={importing}
          disabled={!file}
          onClick={handleImport}
        >
          开始导入
        </Button>
      </div>
      {result && !result.success ? (
        <div className='mt-4'>
          <Alert
            type='error'
            showIcon
            title={`共 ${result.total} 条数据，发现 ${result.errors.length} 处错误，本次没有导入任何候选人`}
          />
          <Table
            className='mt-3'
            size='small'
            rowKey={(row) => `${row.rowIndex}-${row.message}`}
            pagination={false}
            dataSource={result.errors}
            columns={[
              {
                title: '行号',
                dataIndex: 'rowIndex',
                width: 80,
                render: (value: number) => (value ? `第 ${value} 行` : '—'),
              },
              { title: '错误', dataIndex: 'message' },
            ]}
          />
        </div>
      ) : null}
    </BaseModalForm>
  );
}
