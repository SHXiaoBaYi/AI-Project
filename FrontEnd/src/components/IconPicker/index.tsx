import React from 'react';
import { useState, useMemo, createElement } from 'react';
import { Input, Popover, Tooltip, Empty, theme } from 'antd';
import { SearchOutlined } from '@ant-design/icons';
import { getIconComponent, getIconNames } from '@/utils/iconMap';

interface IconPickerProps {
  value?: string;
  onChange?: (value: string) => void;
  style?: React.CSSProperties;
  className?: string;
}

const allIconNames = getIconNames();

export default function IconPicker({ value, onChange, style, className }: IconPickerProps) {
  const { token } = theme.useToken();
  const [open, setOpen] = useState(false);
  const [search, setSearch] = useState('');

  const filteredIcons = useMemo(() => {
    if (!search) return allIconNames;
    const keyword = search.toLowerCase();
    return allIconNames.filter((name) => name.toLowerCase().includes(keyword));
  }, [search]);

  const SelectedIcon = value ? getIconComponent(value) : undefined;

  return (
    <Popover
      open={open}
      onOpenChange={(v) => {
        setOpen(v);
        if (!v) setSearch('');
      }}
      trigger='click'
      placement='bottomLeft'
      styles={{ content: { width: 400, maxHeight: 360, overflow: 'hidden', padding: 0 } }}
      content={
        <div className='flex h-90 flex-col'>
          <div className='px-3 pt-3 pb-2'>
            <Input
              prefix={<SearchOutlined className='text-[#999]' />}
              placeholder='搜索图标'
              allowClear
              value={search}
              onChange={(e) => setSearch(e.target.value)}
              autoFocus
            />
          </div>
          <div className='flex-1 overflow-auto px-3 pb-3'>
            {filteredIcons.length > 0 ? (
              <div className='grid grid-cols-6 gap-1'>
                {filteredIcons.map((name) => {
                  const IconComp = getIconComponent(name)!;
                  const isSelected = name === value;
                  return (
                    <Tooltip
                      key={name}
                      title={name}
                      placement='top'
                    >
                      <div
                        className={`flex cursor-pointer flex-col items-center justify-center rounded py-2 ${isSelected ? '' : 'hover:bg-gray-50'}`}
                        style={
                          isSelected ? { backgroundColor: token.colorPrimaryBg, color: token.colorPrimary } : undefined
                        }
                        onClick={() => {
                          onChange?.(name);
                          setOpen(false);
                          setSearch('');
                        }}
                      >
                        {/* 使用createElement形式避免重复渲染 */}
                        {createElement(IconComp, { className: 'text-xl' })}
                        <span className='mt-1 w-full truncate text-center text-[10px]'>
                          {name.replace('Outlined', '')}
                        </span>
                      </div>
                    </Tooltip>
                  );
                })}
              </div>
            ) : (
              <Empty
                image={Empty.PRESENTED_IMAGE_SIMPLE}
                description='未找到图标'
                className='mt-10'
              />
            )}
          </div>
        </div>
      }
    >
      <div
        className={`hover:border-primary flex cursor-pointer items-center gap-2 rounded border border-gray-300 px-3 py-1 min-w-50${className ? ` ${className}` : ''}`}
        onClick={() => setOpen(!open)}
      >
        {SelectedIcon ? (
          <>
            {/* 使用createElement形式避免重复渲染 */}
            {createElement(SelectedIcon!, { className: 'text-xl', style })}
            <span className='text-sm'>{value}</span>
          </>
        ) : (
          <span className='text-sm text-[#999]'>请选择图标</span>
        )}
      </div>
    </Popover>
  );
}
