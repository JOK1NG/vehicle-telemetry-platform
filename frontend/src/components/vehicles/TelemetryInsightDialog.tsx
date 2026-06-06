import { useEffect, useMemo, useRef, useState } from 'react';
import { createPortal } from 'react-dom';
import { aiApi } from '../../api/ai';
import type { TelemetryInsightResponse, Vehicle } from '../../types';
import { cx } from '../common/utils';
import { toast } from '../common/Toast';
import { XIcon, BrainIcon } from '../common/Icons';

type WindowKey = '15m' | '1h' | '24h';

const WINDOWS: { key: WindowKey; label: string; ms: number }[] = [
  { key: '15m', label: '过去 15 分钟', ms: 15 * 60 * 1000 },
  { key: '1h', label: '过去 1 小时', ms: 60 * 60 * 1000 },
  { key: '24h', label: '过去 24 小时', ms: 24 * 60 * 60 * 1000 },
];

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

function computeRange(key: WindowKey): { start: string; end: string; label: string } {
  const win = WINDOWS.find((w) => w.key === key)!;
  const end = new Date();
  const start = new Date(end.getTime() - win.ms);
  return {
    start: start.toISOString(),
    end: end.toISOString(),
    label: win.label,
  };
}

export function TelemetryInsightDialog({
  vehicle,
  onClose,
}: {
  vehicle: Vehicle;
  onClose: () => void;
}) {
  const [windowKey, setWindowKey] = useState<WindowKey>('15m');
  const [loading, setLoading] = useState(false);
  const [result, setResult] = useState<TelemetryInsightResponse | null>(null);
  const [streamText, setStreamText] = useState('');
  const [closing, setClosing] = useState(false);
  const abortRef = useRef<AbortController | null>(null);

  const handleClose = () => {
    if (closing) return;
    abortRef.current?.abort();
    setClosing(true);
    window.setTimeout(() => onClose(), 180);
  };

  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape') handleClose();
    };
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [onClose, closing]);

  useEffect(() => {
    return () => abortRef.current?.abort();
  }, []);

  const range = useMemo(() => computeRange(windowKey), [windowKey]);

  const handleAnalyze = async () => {
    abortRef.current?.abort();
    const controller = new AbortController();
    abortRef.current = controller;
    setLoading(true);
    setResult(null);
    setStreamText('');
    try {
      const res = await aiApi.telemetryInsightStream(
        {
          vehicleId: vehicle.id,
          timeRange: { start: range.start, end: range.end },
        },
        {
          signal: controller.signal,
          onDelta: (delta) => setStreamText((prev) => prev + delta),
          onFinal: (finalResult) => setResult(finalResult),
        }
      );
      setResult(res);
    } catch (e) {
      if (controller.signal.aborted) return;
      toast.error(e instanceof Error ? e.message : 'AI 诊断失败');
    } finally {
      if (abortRef.current === controller) {
        abortRef.current = null;
        setLoading(false);
      }
    }
  };

  return createPortal(
    <div
      className={cx(
        'fixed inset-0 z-50 grid place-items-center bg-black/60 p-4',
        closing ? 'modal-backdrop-out' : 'modal-backdrop-in'
      )}
      onClick={handleClose}
    >
      <div
        onClick={(e) => e.stopPropagation()}
        className={cx(
          'w-full max-w-[640px] max-h-[85vh] overflow-hidden flex flex-col bg-[var(--card)] rounded-xl border border-[var(--border)] shadow-2xl',
          closing ? 'modal-card-out' : 'modal-card-in'
        )}
      >
        <div className="px-5 py-3.5 border-b border-[var(--border)] flex items-center justify-between gap-3">
          <div className="flex items-center gap-2 min-w-0">
            <BrainIcon className="w-4 h-4 text-[var(--primary)] shrink-0" />
            <div className="min-w-0">
              <h3 className="text-[14px] font-semibold tracking-tight">AI 遥测诊断</h3>
              <div className="text-[11.5px] text-[var(--muted-foreground)] truncate">
                {vehicle.plateNo}
                {vehicle.model ? ` · ${vehicle.model}` : ''}
                {vehicle.vin ? ` · ${vehicle.vin}` : ''}
              </div>
            </div>
          </div>
          <button
            onClick={handleClose}
            className="w-7 h-7 grid place-items-center rounded-md text-[var(--muted-foreground)] hover:bg-[var(--muted)] hover:text-[var(--foreground)]"
          >
            <XIcon className="w-3.5 h-3.5" />
          </button>
        </div>

        <div className="px-5 py-4 border-b border-[var(--border)] space-y-3">
          <div className="flex items-center justify-between gap-3 flex-wrap">
            <div className="flex items-center rounded-md border border-[var(--input)] bg-[var(--background)] p-0.5">
              {WINDOWS.map((w) => (
                <button
                  key={w.key}
                  onClick={() => setWindowKey(w.key)}
                  className={cx(
                    'h-7 px-2.5 rounded text-[12px] font-medium transition-colors',
                    windowKey === w.key
                      ? 'bg-[var(--primary)] text-[var(--primary-foreground)]'
                      : 'text-[var(--muted-foreground)] hover:text-[var(--foreground)]'
                  )}
                >
                  {w.label}
                </button>
              ))}
            </div>
            <button
              onClick={handleAnalyze}
              disabled={loading}
              className="h-8 px-3 rounded-md text-[12.5px] font-medium border border-[var(--border)] bg-[var(--primary)] text-[var(--primary-foreground)] hover:opacity-95 disabled:opacity-60 inline-flex items-center gap-1.5"
            >
              {loading ? (
                <>
                  <span className="w-3 h-3 border-2 border-[var(--primary-foreground)]/30 border-t-[var(--primary-foreground)] rounded-full animate-spin" />
                  分析中...
                </>
              ) : (
                <>
                  <BrainIcon className="w-3.5 h-3.5" />
                  开始分析
                </>
              )}
            </button>
          </div>
          <div className="text-[11.5px] text-[var(--muted-foreground)]">
            时间窗口：{range.label} · 后端会查询 speed / heading / battery 最近 200 条样本
          </div>
        </div>

        <div className="px-5 py-4 overflow-y-auto flex-1">
          {!result && !loading && (
            <div className="text-center py-10 text-[var(--muted-foreground)] text-[12.5px]">
              选择时间窗口后点击「开始分析」，AI 将基于该车辆最近遥测片段给出诊断结论。
            </div>
          )}

          {loading && (
            <div className="py-8 text-[var(--muted-foreground)] text-[12.5px]">
              <div className="text-center">
                <div className="w-5 h-5 mx-auto mb-2 border-2 border-[var(--muted-foreground)]/30 border-t-[var(--foreground)] rounded-full animate-spin" />
                模型正在流式生成结构化诊断...
              </div>
              <div className="mt-4 rounded-md border border-[var(--border)] bg-[var(--background)] p-3 min-h-[96px] max-h-[220px] overflow-y-auto">
                {streamText ? (
                  <pre className="whitespace-pre-wrap break-words text-left text-[11.5px] leading-relaxed font-mono text-[var(--foreground)]">
                    {streamText}
                  </pre>
                ) : (
                  <div className="h-full min-h-[70px] grid place-items-center text-[11.5px]">
                    等待首个输出片段...
                  </div>
                )}
              </div>
            </div>
          )}

          {result && !loading && (
            <div className="space-y-4">
              <div className="flex items-center gap-2">
                <span
                  className={cx(
                    'text-[11px] px-1.5 py-0.5 rounded-full border font-medium',
                    severityBg[result.severity] ?? 'bg-[var(--muted)] border-[var(--border)]'
                  )}
                >
                  {result.severity}
                </span>
                <span
                  className={cx(
                    'text-[11.5px] font-semibold',
                    severityColor[result.severity] ?? 'text-[var(--muted-foreground)]'
                  )}
                >
                  严重级别
                </span>
              </div>

              <p className="text-[13.5px] leading-relaxed">{result.summary}</p>

              {result.findings.length > 0 && (
                <div>
                  <div className="text-[12.5px] font-semibold text-[var(--foreground)] mb-1.5">
                    发现
                  </div>
                  <ul className="space-y-1.5">
                    {result.findings.map((f, i) => (
                      <li
                        key={i}
                        className="text-[12.5px] leading-relaxed flex gap-2"
                      >
                        <span className="text-[var(--muted-foreground)] shrink-0 mt-0.5">
                          •
                        </span>
                        <span>{f}</span>
                      </li>
                    ))}
                  </ul>
                </div>
              )}

              {result.recommendations.length > 0 && (
                <div>
                  <div className="text-[12.5px] font-semibold text-[var(--foreground)] mb-1.5">
                    建议
                  </div>
                  <ul className="space-y-1.5">
                    {result.recommendations.map((r, i) => (
                      <li
                        key={i}
                        className="text-[12.5px] leading-relaxed flex gap-2"
                      >
                        <span className="text-[var(--muted-foreground)] shrink-0 mt-0.5">
                          →
                        </span>
                        <span>{r}</span>
                      </li>
                    ))}
                  </ul>
                </div>
              )}

              {streamText && (
                <details className="rounded-md border border-[var(--border)] bg-[var(--background)]">
                  <summary className="cursor-pointer px-3 py-2 text-[11.5px] text-[var(--muted-foreground)]">
                    流式生成原文
                  </summary>
                  <pre className="max-h-[180px] overflow-y-auto px-3 pb-3 whitespace-pre-wrap break-words text-[11.5px] leading-relaxed font-mono">
                    {streamText}
                  </pre>
                </details>
              )}

              <div className="text-[11.5px] text-[var(--muted-foreground)] pt-2 border-t border-[var(--border)]">
                耗时 {result.latencyMs}ms · AI 结论仅供辅助参考，关键故障以人工检修为准
              </div>
            </div>
          )}
        </div>
      </div>
    </div>,
    document.body
  );
}
