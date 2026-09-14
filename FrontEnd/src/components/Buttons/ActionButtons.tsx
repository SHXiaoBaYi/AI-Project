import { useState } from 'react';
import { Dropdown, Button, Popconfirm, Space } from 'antd';
import { EllipsisOutlined } from '@ant-design/icons';
import { usePermission } from '@/hooks/usePermission';
import type { ReactNode } from 'react';

export interface ActionItem {
  key: string;
  perm?: string;
  label: string;
  icon?: ReactNode;
  onClick?: () => void;
  confirmTitle?: string;
}

interface ActionButtonsProps {
  items: ActionItem[];
  maxVisible?: number;
}

export default function ActionButtons({ items, maxVisible = 3 }: ActionButtonsProps) {
  const { has } = usePermission();
  const [activeConfirm, setActiveConfirm] = useState<string | null>(null);
  const [dropdownOpen, setDropdownOpen] = useState(false);

  const closeConfirmAndDropdown = () => {
    setActiveConfirm(null);
    setTimeout(() => setDropdownOpen(false), 200);
  };

  const visibleItems = items.filter((item) => !item.perm || has(item.perm));

  if (visibleItems.length === 0) return null;

  const primary = visibleItems.slice(0, maxVisible);
  const overflow = visibleItems.slice(maxVisible);
  const hasConfirmActive = activeConfirm !== null;

  const renderButton = (item: ActionItem, block: boolean) => (
    <Button
      color={block ? 'default' : 'primary'}
      variant={block ? 'text' : 'link'}
      icon={item.icon}
      block={block}
      size='small'
      style={{
        padding: block ? '4px 10px' : '0px',
        height: block ? '30px' : undefined,
        justifyContent: block ? 'flex-start' : undefined,
        borderRadius: block ? 0 : undefined,
        border: 'none',
        color: block ? '#333639' : undefined,
      }}
    >
      {item.label}
    </Button>
  );

  const renderPrimaryItem = (item: ActionItem) => {
    const btn = renderButton(item, false);
    if (item.confirmTitle) {
      return (
        <Popconfirm
          title={item.confirmTitle}
          onConfirm={item.onClick}
          okText='确定'
          cancelText='取消'
        >
          {btn}
        </Popconfirm>
      );
    }
    return <span onClick={item.onClick}>{btn}</span>;
  };

  const renderOverflowItem = (item: ActionItem) => {
    const btn = renderButton(item, true);

    if (item.confirmTitle) {
      return (
        <Popconfirm
          title={item.confirmTitle}
          open={activeConfirm === item.key}
          onOpenChange={(open) => {
            if (open) {
              setActiveConfirm(item.key);
            } else {
              setActiveConfirm(null);
            }
          }}
          onConfirm={() => {
            item.onClick?.();
            closeConfirmAndDropdown();
          }}
          onCancel={() => {
            closeConfirmAndDropdown();
          }}
          okText='确定'
          cancelText='取消'
        >
          <div className='w-full'>{btn}</div>
        </Popconfirm>
      );
    }

    return (
      <span
        onClick={() => {
          item.onClick?.();
          closeConfirmAndDropdown();
        }}
        className='block w-full'
      >
        {btn}
      </span>
    );
  };

  return (
    <div className='flex items-center'>
      <Space size={10}>
        {primary.map((item) => (
          <span key={item.key}>{renderPrimaryItem(item)}</span>
        ))}
        {overflow.length > 0 && (
          <Dropdown
            trigger={['hover']}
            open={hasConfirmActive ? true : dropdownOpen}
            onOpenChange={(open) => {
              if (!hasConfirmActive) {
                setDropdownOpen(open);
              }
            }}
            popupRender={() => (
              <div className='flex min-w-2 flex-col rounded bg-white p-2 shadow-md'>
                {overflow.map((item) => (
                  <div key={item.key}>{renderOverflowItem(item)}</div>
                ))}
              </div>
            )}
          >
            <EllipsisOutlined style={{ color: 'var(--ant-color-primary)', cursor: 'pointer', fontSize: '16px' }} />
          </Dropdown>
        )}
      </Space>
    </div>
  );
}
