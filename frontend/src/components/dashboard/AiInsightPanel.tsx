import { useEffect, useRef, useState } from 'react';
import { aiApi } from '../../api/ai';
import type { DashboardInsightResponse } from '../../types';
import { cx } from '../common/utils';
import { ChevronDownIcon } from '../common/Icons';
import { toast } from '../common/Toast';

const severityColor: Record<string, string> = {
  LOW: 'text-[var(--chart-2)]',
  MEDIUM: 'text-amber-500',
  HIGH: 'text-orange-500',
  CRITICAL: 'text-[var(--destructive)]',
};

const severityBg: Record<string, string> = {
  LOW: 'bg-[var(--chart-2)]/10 border-[var(--chart-2)]/25',
  MEDIUM: 'bg-amber-500/10 border-amber-500/25',
  HIGH: 'bg-orange-500/10 border-orange-500/25',
  CRITICAL: 'bg-[var(--destructive)]/10 border-[var(--destructive)]/25',
};

const formatMs = (ms?: number) => `${Math.round(ms ?? 0)}ms`;

export function AiInsightPanel({
  onlineCount,
  avgSpeed,
  avgBattery,
}: {
  onlineCount: number;
  avgSpeed: number;
  avgBattery: number;
}) {
  const [loading, setLoading] = useState(false);
  const [result, setResult] = useState<DashboardInsightResponse | null>(null);
  const [collapsed, setCollapsed] = useState(true);
  const [showScrollHint, setShowScrollHint] = useState(false);
  const contentRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    const el = contentRef.current;
    if (!el) return;
    const check = () => {
      const hasOverflow = el.scrollHeight - el.clientHeight > 1;
      const atBottom = el.scrollHeight - el.scrollTop - el.clientHeight < 1;
      setShowScrollHint(hasOverflow && !atBottom);
    };
    check();
    el.addEventListener('scroll', check);
    const ro = new ResizeObserver(check);
    ro.observe(el);
    return () => {
      el.removeEventListener('scroll', check);
      ro.disconnect();
    };
  }, [result, collapsed]);

  const handleAnalyze = async () => {
    setLoading(true);
    try {
      const res = await aiApi.dashboardInsight({
        summaryStats: {
          onlineCount,
          avgSpeed: Math.round(avgSpeed * 10) / 10,
          avgBattery: Math.round(avgBattery * 10) / 10,
        },
      });
      setResult(res);
      setCollapsed(false);
    } catch (e) {
      toast.error(e instanceof Error ? e.message : 'AI 分析失败');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="rounded-xl border border-[var(--border)] bg-[var(--card)] overflow-hidden shrink-0">
      <div
        className="px-4 py-3 flex items-center justify-between cursor-pointer select-none"
        onClick={() => result && setCollapsed(!collapsed)}
      >
        <div className="flex items-center gap-2">
          <span className="text-[13px] font-semibold">🤖 AI 大屏解读</span>
          {result && (
            <span
              className={cx(
                'text-[11px] px-1.5 py-0.5 rounded-full border font-medium',
                severityBg[result.severity] ?? 'bg-[var(--muted)] border-[var(--border)]'
              )}
            >
              {result.severity}
            </span>
          )}
        </div>
        <button
          onClick={(e) => {
            e.stopPropagation();
            handleAnalyze();
          }}
          disabled={loading}
          className="h-7 px-2.5 rounded-md text-[12px] font-medium border border-[var(--border)] bg-[var(--muted)] hover:bg-[var(--muted-foreground)]/10 disabled:opacity-60 inline-flex items-center gap-1.5"
        >
          {loading ? (
            <>
              <span className="w-3 h-3 border-2 border-[var(--muted-foreground)]/30 border-t-[var(--foreground)] rounded-full animate-spin" />
              分析中...
            </>
          ) : (
            '开始分析'
          )}
        </button>
      </div>

      {result && !collapsed && (
        <div className="relative border-t border-[var(--border)]">
          <div
            ref={contentRef}
            className="px-4 pb-4 pt-3 space-y-4 max-h-[min(260px,38vh)] overflow-y-auto [scrollbar-width:none] [&::-webkit-scrollbar]:hidden"
          >
          <p className="text-[16px] leading-relaxed">{result.summary}</p>

          {result.findings.length > 0 && (
            <div>
              <div className="text-[13px] font-semibold text-[var(--muted-foreground)] mb-2">
                发现
              </div>
              <ul className="space-y-2">
                {result.findings.map((f, i) => (
                  <li key={i} className="text-[16px] leading-relaxed flex gap-2">
                    <span className="text-[var(--muted-foreground)] shrink-0 mt-0.5">•</span>
                    <span>
                      {f.type && (
                        <span className="mr-2 rounded border border-[var(--border)] bg-[var(--muted)] px-1.5 py-0.5 text-[12px] font-semibold text-[var(--muted-foreground)]">
                          {f.type}
                        </span>
                      )}
                      <span>{f.description}</span>
                      {f.detail && (
                        <span className="block mt-1 text-[14px] text-[var(--muted-foreground)]">
                          {f.detail}
                        </span>
                      )}
                    </span>
                  </li>
                ))}
              </ul>
            </div>
          )}

          {result.recommendations.length > 0 && (
            <div>
              <div className="text-[13px] font-semibold text-[var(--muted-foreground)] mb-2">
                建议
              </div>
              <ul className="space-y-2">
                {result.recommendations.map((r, i) => (
                  <li key={i} className="text-[16px] leading-relaxed flex gap-2">
                    <span className="text-[var(--muted-foreground)] shrink-0 mt-0.5">→</span>
                    <span>{r}</span>
                  </li>
                ))}
              </ul>
            </div>
          )}

          <div className="text-[12px] text-[var(--muted-foreground)]">
            总耗时 {formatMs(result.timing?.totalMs ?? result.latencyMs)}
            {result.timing && (
              <>
                {' '}· 截图 {formatMs(result.timing.screenshotMs)}
                {' '}· 模型 {formatMs(result.timing.modelMs)}
                {' '}· {result.timing.imageInput ? '图片输入' : '文本输入'}
              </>
            )}
            {' '}· AI 结论仅供辅助参考
          </div>
          </div>
          {showScrollHint && (
            <div className="pointer-events-none absolute bottom-0 left-0 right-0 h-10 bg-gradient-to-t from-[var(--card)] via-[var(--card)]/70 to-transparent flex items-end justify-center pb-1.5">
              <ChevronDownIcon className="w-3.5 h-3.5 text-[var(--muted-foreground)] scroll-hint" />
            </div>
          )}
        </div>
      )}
    </div>
  );
}
