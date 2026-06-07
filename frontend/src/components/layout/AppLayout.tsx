import { Outlet } from 'react-router-dom';
import { Sidebar } from './Sidebar';
import { AlertBell } from '../alerts/AlertBell';

export function AppLayout() {
  return (
    <div className="flex h-screen w-full bg-[var(--background)] text-[var(--foreground)]">
      <Sidebar vehicleCount={0} />
      <main className="flex-1 min-w-0 overflow-auto">
        <div className="max-w-[1400px] mx-auto p-5 md:p-6 lg:p-7 h-full">
          <div className="flex items-center justify-end mb-3 -mt-1">
            <AlertBell />
          </div>
          <Outlet />
        </div>
      </main>
    </div>
  );
}
