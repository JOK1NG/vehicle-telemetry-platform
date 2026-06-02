import { useState } from 'react';
import { useLocation, useNavigate } from 'react-router-dom';
import { authApi } from '../../api/auth';
import { useAuthStore } from '../../stores/auth';
import { toast } from '../common/Toast';
import { LogoIcon, UserIcon, LockIcon } from '../common/Icons';
import { cx } from '../common/utils';

const TEST_ACCOUNTS = [
  { user: 'admin', pwd: 'admin123', role: 'ADMIN' as const, desc: '可维护车辆' },
  { user: 'viewer', pwd: 'viewer123', role: 'VIEWER' as const, desc: '仅查看' },
];

export function LoginView() {
  const navigate = useNavigate();
  const location = useLocation();
  const setAuth = useAuthStore((s) => s.setAuth);

  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [loading, setLoading] = useState(false);
  const [errors, setErrors] = useState<{ username?: string; password?: string }>({});
  const [globalError, setGlobalError] = useState<string | null>(null);
  const [showPwd, setShowPwd] = useState(false);

  const submit = async (e: React.FormEvent) => {
    e.preventDefault();
    const errs: typeof errors = {};
    if (!username.trim()) errs.username = '请输入用户名';
    if (!password) errs.password = '请输入密码';
    setErrors(errs);
    setGlobalError(null);
    if (Object.keys(errs).length) return;

    setLoading(true);
    try {
      const res = await authApi.login({ username: username.trim(), password });
      setAuth(res.token, res.user);
      toast.success(`欢迎回来，${res.user.username}`);
      const params = new URLSearchParams(location.search);
      const redirect = params.get('redirect') || '/vehicles';
      navigate(redirect, { replace: true });
    } catch (err) {
      setGlobalError(err instanceof Error ? err.message : '登录失败');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="min-h-screen w-full flex items-center justify-center bg-[var(--background)] px-4 py-10 relative overflow-hidden">
      <div className="absolute inset-0 -z-10">
        <div className="absolute inset-0 bg-[radial-gradient(circle_at_15%_20%,oklch(0.94_0.04_200)_0%,transparent_50%),radial-gradient(circle_at_85%_75%,oklch(0.95_0.05_180)_0%,transparent_55%)]" />
        <div
          className="absolute inset-0 opacity-[0.35]"
          style={{
            backgroundImage:
              'linear-gradient(to right, oklch(0.92 0.005 240) 1px, transparent 1px), linear-gradient(to bottom, oklch(0.92 0.005 240) 1px, transparent 1px)',
            backgroundSize: '44px 44px',
            maskImage: 'radial-gradient(ellipse at center, black 30%, transparent 75%)',
            WebkitMaskImage: 'radial-gradient(ellipse at center, black 30%, transparent 75%)',
          }}
        />
      </div>

      <div className="w-full max-w-[420px]">
        <div className="flex items-center gap-2.5 mb-8 justify-center">
          <div className="w-9 h-9 rounded-lg bg-[var(--primary)] text-[var(--primary-foreground)] grid place-items-center">
            <LogoIcon className="w-5 h-5" />
          </div>
          <div className="leading-tight">
            <div className="text-[15px] font-semibold">车辆遥测平台</div>
            <div className="text-[11px] uppercase tracking-[0.14em] text-[var(--muted-foreground)]">
              Fleet Telemetry · v2.0
            </div>
          </div>
        </div>

        <div className="rounded-xl border border-[var(--border)] bg-[var(--card)] shadow-[0_1px_2px_oklch(0.5_0_0/0.04),0_8px_24px_-12px_oklch(0.4_0_0/0.10)]">
          <div className="px-7 pt-7 pb-2">
            <h1 className="text-[20px] font-semibold tracking-tight">欢迎回来</h1>
            <p className="text-sm text-[var(--muted-foreground)] mt-1">使用您的账号登录控制台</p>
          </div>

          <form onSubmit={submit} className="px-7 pb-6 pt-3 space-y-3.5" noValidate>
            <label className="block">
              <span className="text-xs font-medium text-[var(--muted-foreground)]">用户名</span>
              <div
                className={cx(
                  'mt-1.5 flex items-center gap-2 rounded-md border bg-[var(--background)] px-3 h-10 transition-colors',
                  errors.username
                    ? 'border-[var(--destructive)] focus-within:border-[var(--destructive)]'
                    : 'border-[var(--input)] focus-within:border-[var(--ring)] focus-within:ring-2 focus-within:ring-[var(--ring)]/15'
                )}
              >
                <UserIcon className="w-4 h-4 text-[var(--muted-foreground)]" />
                <input
                  type="text"
                  value={username}
                  onChange={(e) => {
                    setUsername(e.target.value);
                    if (errors.username) setErrors((p) => ({ ...p, username: undefined }));
                    setGlobalError(null);
                  }}
                  placeholder="admin 或 viewer"
                  className="flex-1 bg-transparent outline-none text-sm placeholder:text-[var(--muted-foreground)]/70"
                  autoComplete="username"
                />
              </div>
              {errors.username && (
                <span className="text-[11px] text-[var(--destructive)] mt-1 block">
                  {errors.username}
                </span>
              )}
            </label>

            <label className="block">
              <span className="text-xs font-medium text-[var(--muted-foreground)]">密码</span>
              <div
                className={cx(
                  'mt-1.5 flex items-center gap-2 rounded-md border bg-[var(--background)] px-3 h-10 transition-colors',
                  errors.password
                    ? 'border-[var(--destructive)]'
                    : 'border-[var(--input)] focus-within:border-[var(--ring)] focus-within:ring-2 focus-within:ring-[var(--ring)]/15'
                )}
              >
                <LockIcon className="w-4 h-4 text-[var(--muted-foreground)]" />
                <input
                  type={showPwd ? 'text' : 'password'}
                  value={password}
                  onChange={(e) => {
                    setPassword(e.target.value);
                    if (errors.password) setErrors((p) => ({ ...p, password: undefined }));
                    setGlobalError(null);
                  }}
                  placeholder="••••••••"
                  className="flex-1 bg-transparent outline-none text-sm placeholder:text-[var(--muted-foreground)]/70"
                  autoComplete="current-password"
                />
                <button
                  type="button"
                  onClick={() => setShowPwd((p) => !p)}
                  className="text-[11px] text-[var(--muted-foreground)] hover:text-[var(--foreground)] px-1.5"
                  tabIndex={-1}
                >
                  {showPwd ? '隐藏' : '显示'}
                </button>
              </div>
              {errors.password && (
                <span className="text-[11px] text-[var(--destructive)] mt-1 block">
                  {errors.password}
                </span>
              )}
            </label>

            {globalError && (
              <div className="rounded-md bg-[var(--destructive)]/8 text-[var(--destructive)] text-xs px-3 py-2 border border-[var(--destructive)]/20">
                {globalError}
              </div>
            )}

            <button
              type="submit"
              disabled={loading}
              className={cx(
                'w-full h-10 rounded-md text-sm font-medium transition-all',
                'bg-[var(--primary)] text-[var(--primary-foreground)]',
                'hover:opacity-95 active:scale-[0.99]',
                'disabled:opacity-60 disabled:cursor-not-allowed',
                'flex items-center justify-center gap-2'
              )}
            >
              {loading ? (
                <>
                  <span className="w-3.5 h-3.5 border-2 border-current border-t-transparent rounded-full animate-spin" />
                  正在登录…
                </>
              ) : (
                '登 录'
              )}
            </button>
          </form>

          <div className="border-t border-[var(--border)] bg-[var(--muted)]/40 rounded-b-xl px-7 py-4">
            <div className="text-[11px] font-medium uppercase tracking-wider text-[var(--muted-foreground)] mb-2">
              测试账号
            </div>
            <div className="grid grid-cols-2 gap-2">
              {TEST_ACCOUNTS.map((acc) => (
                <button
                  key={acc.user}
                  type="button"
                  onClick={() => {
                    setUsername(acc.user);
                    setPassword(acc.pwd);
                    setErrors({});
                    setGlobalError(null);
                  }}
                  className="text-left rounded-md border border-[var(--border)] bg-[var(--card)] px-2.5 py-2 hover:border-[var(--ring)]/50 hover:bg-[var(--accent)]/30 transition-colors"
                >
                  <div className="flex items-center justify-between">
                    <span className="text-xs font-mono font-medium">{acc.user}</span>
                    <span
                      className={cx(
                        'text-[9px] px-1.5 py-0.5 rounded font-medium',
                        acc.role === 'ADMIN'
                          ? 'bg-[var(--primary)]/12 text-[var(--primary)]'
                          : 'bg-[var(--muted)] text-[var(--muted-foreground)]'
                      )}
                    >
                      {acc.role}
                    </span>
                  </div>
                  <div className="text-[10px] text-[var(--muted-foreground)] mt-0.5 font-mono">
                    {acc.pwd}
                  </div>
                </button>
              ))}
            </div>
          </div>
        </div>

        <div className="text-center text-[11px] text-[var(--muted-foreground)] mt-6">
          © 2025 Fleet Telemetry · 仅供内部演示
        </div>
      </div>
    </div>
  );
}
