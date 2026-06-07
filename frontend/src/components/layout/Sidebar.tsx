import type { ReactNode } from 'react';
import { NavLink, useNavigate } from 'react-router-dom';
import { useAuthStore } from '../../stores/auth';
import {
  LogoIcon,
  DashboardIcon,
  VehiclesIcon,
  LogoutIcon,
  SparkleIcon,
  BellIcon,
  RouteIcon,
  FenceIcon,
} from '../common/Icons';
import { useAlertsStore } from '../../stores/alerts';
import { cx } from '../common/utils';

function NavItem({
  to,
  icon,
  label,
  badge,
}: {
  to: string;
  icon: ReactNode;
  label: string;
  badge?: string | number;
}) {
  return (
    <NavLink
      to={to}
      className={({ isActive }) =>
        cx(
          'group w-full flex items-center gap-2.5 px-3 h-9 rounded-md text-[13px] font-medium transition-colors',
          isActive
            ? 'bg-[var(--sidebar-accent)] text-[var(--sidebar-accent-foreground)]'
            : 'text-[var(--sidebar-foreground)]/75 hover:bg-[var(--sidebar-accent)]/60 hover:text-[var(--sidebar-foreground)]'
        )
      }
    >
      {({ isActive }) => (
        <>
          <span
            className={cx(
              'shrink-0 w-4 h-4',
              isActive
                ? 'text-[var(--sidebar-primary)]'
                : 'text-[var(--muted-foreground)] group-hover:text-[var(--sidebar-foreground)]'
            )}
          >
            {icon}
          </span>
          <span className="flex-1 text-left">{label}</span>
          {badge !== undefined && (
            <span
              className={cx(
                'text-[10px] font-mono px-1.5 h-[18px] grid place-items-center rounded',
                isActive
                  ? 'bg-[var(--sidebar-primary)]/12 text-[var(--sidebar-primary)]'
                  : 'bg-[var(--muted)] text-[var(--muted-foreground)]'
              )}
            >
              {badge}
            </span>
          )}
          {isActive && <span className="w-1 h-4 rounded-full bg-[var(--sidebar-primary)]" />}
        </>
      )}
    </NavLink>
  );
}

export function Sidebar({ vehicleCount }: { vehicleCount: number }) {
  const navigate = useNavigate();
  const user = useAuthStore((s) => s.user);
  const logout = useAuthStore((s) => s.logout);
  const lastSeenAt = useAlertsStore((s) => s.lastSeenAt);
  const alerts = useAlertsStore((s) => s.items);
  const unread = (() => {
    if (!lastSeenAt) return alerts.filter((a) => !a.handled).length;
    const since = new Date(lastSeenAt).getTime();
    return alerts.filter((a) => !a.handled && new Date(a.occurredAt).getTime() > since).length;
  })();

  if (!user) return null;

  const handleLogout = () => {
    logout();
    navigate('/login', { replace: true });
  };

  return (
    <aside className="w-[232px] shrink-0 border-r border-[var(--sidebar-border)] bg-[var(--sidebar)] flex flex-col">
      <div className="h-14 px-4 flex items-center gap-2.5 border-b border-[var(--sidebar-border)]">
        <div className="w-7 h-7 rounded-md bg-[var(--primary)] text-[var(--primary-foreground)] grid place-items-center">
          <LogoIcon className="w-4 h-4" />
        </div>
        <div className="leading-tight min-w-0">
          <div className="text-[13px] font-semibold tracking-tight truncate">车辆遥测平台</div>
          <div className="text-[9px] uppercase tracking-[0.16em] text-[var(--muted-foreground)]">
            Fleet · v2.0
          </div>
        </div>
      </div>

      <nav className="p-2.5 space-y-0.5">
        <div className="px-2.5 py-2 text-[10px] font-semibold uppercase tracking-[0.12em] text-[var(--muted-foreground)]">
          工作台
        </div>
        <NavItem to="/dashboard" icon={<DashboardIcon className="w-4 h-4" />} label="监控大屏" />
        <NavItem
          to="/vehicles"
          icon={<VehiclesIcon className="w-4 h-4" />}
          label="车辆列表"
          badge={vehicleCount}
        />
        <NavItem to="/alerts" icon={<BellIcon className="w-4 h-4" />} label="告警中心" badge={unread || undefined} />
        <NavItem to="/trajectory" icon={<RouteIcon className="w-4 h-4" />} label="轨迹回放" />
        <NavItem to="/geofences" icon={<FenceIcon className="w-4 h-4" />} label="地理围栏" />
      </nav>

      <div className="px-2.5 py-2 mt-1">
        <div className="rounded-lg border border-[var(--border)] bg-[var(--card)] p-3">
          <div className="flex items-center gap-1.5 text-[11px] font-semibold text-[var(--foreground)]">
            <SparkleIcon className="w-3.5 h-3.5 text-[var(--primary)]" />
            数据观察
          </div>
          <p className="text-[11px] text-[var(--muted-foreground)] mt-1.5 leading-relaxed">
            所有遥测使用 GCJ-02 坐标系；后端 1 秒推送一次。
          </p>
        </div>
      </div>

      <div className="mt-auto p-3 border-t border-[var(--sidebar-border)]">
        <div className="flex items-center gap-2.5 px-1.5 py-1.5">
          <div className="w-8 h-8 rounded-full bg-[var(--accent)] text-[var(--accent-foreground)] grid place-items-center text-[12px] font-semibold">
            {user.username.slice(0, 1).toUpperCase()}
          </div>
          <div className="flex-1 min-w-0">
            <div className="text-[12.5px] font-medium truncate leading-tight">{user.username}</div>
            <div className="text-[10px] text-[var(--muted-foreground)] leading-tight">
              {user.role === 'ADMIN' ? '管理员' : '观察员'}
            </div>
          </div>
          <button
            onClick={handleLogout}
            title="退出登录"
            className="w-7 h-7 grid place-items-center rounded-md text-[var(--muted-foreground)] hover:bg-[var(--muted)] hover:text-[var(--foreground)] transition-colors"
          >
            <LogoutIcon className="w-3.5 h-3.5" />
          </button>
        </div>
      </div>
    </aside>
  );
}
