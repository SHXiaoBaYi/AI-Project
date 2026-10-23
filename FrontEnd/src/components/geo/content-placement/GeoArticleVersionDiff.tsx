import { useMemo, useState } from 'react';
import { Checkbox, Tag } from 'antd';
import {
  diffArticleBody,
  diffChars,
  titleDiff,
  type DiffBlock,
  type InlinePart,
  type RefinedDiffBlock,
} from '@/utils/articleDiff';

function InlineDiffText({ parts }: { parts: InlinePart[] }) {
  return (
    <p className='mb-0 text-sm leading-relaxed text-neutral-800'>
      {parts.map((p, i) => {
        if (p.type === 'equal') return <span key={i}>{p.text}</span>;
        if (p.type === 'remove') {
          return (
            <span
              key={i}
              className='rounded-sm bg-red-100 px-0.5 text-red-800 line-through decoration-red-400'
            >
              {p.text}
            </span>
          );
        }
        return (
          <span
            key={i}
            className='rounded-sm bg-emerald-100 px-0.5 font-medium text-emerald-900'
          >
            {p.text}
          </span>
        );
      })}
    </p>
  );
}

function TitleDiffCard({ block }: { block: DiffBlock }) {
  if (block.type === 'equal') {
    return <div className='text-sm text-neutral-800'>{block.text}</div>;
  }
  if (block.type === 'remove') {
    return <div className='text-sm text-red-800 line-through'>{block.text}</div>;
  }
  if (block.type === 'add') {
    return <div className='text-sm font-medium text-emerald-900'>{block.text}</div>;
  }
  return <InlineDiffText parts={diffChars(block.before, block.after)} />;
}

function BodyDiffCard({ block }: { block: RefinedDiffBlock }) {
  if (block.type === 'equal') {
    return (
      <div className='rounded border border-dashed border-neutral-100 px-3 py-1.5 text-xs text-neutral-400'>
        未改：{block.text.length > 100 ? `${block.text.slice(0, 100)}…` : block.text}
      </div>
    );
  }
  if (block.type === 'remove') {
    return (
      <div className='rounded border border-red-200 bg-red-50 px-3 py-2'>
        <div className='mb-1 text-xs font-medium text-red-600'>整段删除</div>
        <div className='text-sm text-red-800 line-through'>{block.text}</div>
      </div>
    );
  }
  if (block.type === 'add') {
    return (
      <div className='rounded border border-emerald-200 bg-emerald-50 px-3 py-2'>
        <div className='mb-1 text-xs font-medium text-emerald-700'>整段新增</div>
        <div className='text-sm text-emerald-900'>{block.text}</div>
      </div>
    );
  }
  return (
    <div className='rounded border border-amber-200 bg-white px-3 py-2 shadow-sm'>
      <div className='mb-1 text-xs font-medium text-amber-700'>段内改字 · 红=删 · 绿=增</div>
      <InlineDiffText parts={block.parts} />
    </div>
  );
}

type Props = {
  leftLabel: string;
  rightLabel: string;
  leftTitle?: string | null;
  rightTitle?: string | null;
  leftBody?: string | null;
  rightBody?: string | null;
};

/** 两轮提审稿的字级版本对照（段对齐 + 段内红删绿增） */
export function GeoArticleVersionDiff({ leftLabel, rightLabel, leftTitle, rightTitle, leftBody, rightBody }: Props) {
  const [showUnchanged, setShowUnchanged] = useState(false);
  const result = useMemo(() => {
    const title = titleDiff(leftTitle, rightTitle);
    const blocks = diffArticleBody(leftBody, rightBody);
    const changedCount = blocks.filter((b) => b.type !== 'equal').length;
    return { title, blocks, changedCount, total: blocks.length };
  }, [leftTitle, rightTitle, leftBody, rightBody]);

  return (
    <div className='min-h-0 flex-1 overflow-auto px-4 py-3'>
      <div className='mb-3 flex flex-wrap items-center gap-2 text-sm'>
        <Tag color='default'>{leftLabel}</Tag>
        <span className='text-neutral-400'>→</span>
        <Tag color='processing'>{rightLabel}</Tag>
        <span className='text-neutral-500'>
          {result.changedCount === 0 ? '正文无差异' : `改动段落 ${result.changedCount} / ${result.total}`}
        </span>
        <span className='text-xs text-neutral-400'>只标改到的字</span>
        <Checkbox
          checked={showUnchanged}
          onChange={(e) => setShowUnchanged(e.target.checked)}
          className='ml-auto text-xs'
        >
          显示未改段落
        </Checkbox>
      </div>

      {result.title && result.title.type !== 'equal' ? (
        <div className='mb-4 rounded border border-amber-200 bg-amber-50/60 px-3 py-2'>
          <div className='mb-1 text-xs font-medium text-neutral-500'>标题</div>
          <TitleDiffCard block={result.title} />
        </div>
      ) : (
        <div className='mb-4 rounded border border-neutral-100 bg-neutral-50 px-3 py-2 text-sm'>
          <span className='text-xs text-neutral-400'>标题相同 · </span>
          {rightTitle || leftTitle || '（无标题）'}
        </div>
      )}

      <div className='mb-1 text-xs font-medium text-neutral-500'>正文版本对照</div>
      {result.blocks.length === 0 ? (
        <div className='text-sm text-neutral-400'>两边都没有可解析正文</div>
      ) : result.changedCount === 0 ? (
        <div className='rounded border border-dashed border-emerald-200 bg-emerald-50/50 px-3 py-6 text-center text-sm text-emerald-800'>
          两轮正文一致，无字级改动
        </div>
      ) : (
        <div className='space-y-2'>
          {result.blocks
            .filter((b) => showUnchanged || b.type !== 'equal')
            .map((block, idx) => (
              <BodyDiffCard
                key={idx}
                block={block}
              />
            ))}
        </div>
      )}
    </div>
  );
}
