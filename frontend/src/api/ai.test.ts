import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { createLocalStorageMock } from '../test/localStorage';

function streamResponse(chunks: string[]): Response {
  const encoder = new TextEncoder();
  return new Response(
    new ReadableStream({
      start(controller) {
        chunks.forEach((chunk) => controller.enqueue(encoder.encode(chunk)));
        controller.close();
      },
    }),
    {
      status: 200,
      headers: { 'Content-Type': 'text/event-stream' },
    }
  );
}

describe('ai api telemetry stream', () => {
  beforeEach(() => {
    vi.stubGlobal('localStorage', createLocalStorageMock());
    vi.resetModules();
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('propagates backend SSE error events instead of replacing them with missing-final errors', async () => {
    const message = 'AI 流式输出未满足接口 JSON schema（复述了输入字段），请重试。';
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(
        streamResponse([`event:error\ndata:{"type":"error","error":"${message}","elapsedMs":12}\n\n`])
      )
    );
    const onError = vi.fn();
    const { aiApi } = await import('./ai');

    await expect(
      aiApi.telemetryInsightStream(
        {
          vehicleId: 1,
          timeRange: {
            start: '2026-06-07T14:41:47.930Z',
            end: '2026-06-07T14:56:47.930Z',
          },
        },
        { onError }
      )
    ).rejects.toThrow(message);

    expect(onError).toHaveBeenCalledWith(message);
  });
});
