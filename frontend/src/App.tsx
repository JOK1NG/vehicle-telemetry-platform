import { Navigate, Route, Routes, useLocation } from 'react-router-dom';
import { useAuth } from './stores/auth';
import { AppLayout } from './components/layout/AppLayout';
import { LoginView } from './components/login/LoginView';
import { DashboardView } from './components/dashboard/DashboardView';
import { VehicleListView } from './components/vehicles/VehicleListView';
import { AlertsView } from './components/alerts/AlertsView';
import { TrajectoryPlaybackView } from './components/trajectory/TrajectoryPlaybackView';
import { GeofenceListView } from './components/geofence/GeofenceListView';
import { ToastContainer } from './components/common/Toast';

function RequireAuth({ children }: { children: React.ReactNode }) {
  const { isLoggedIn } = useAuth();
  const location = useLocation();
  if (!isLoggedIn) {
    return <Navigate to={`/login?redirect=${encodeURIComponent(location.pathname + location.search)}`} replace />;
  }
  return <>{children}</>;
}

function RedirectIfAuthed({ children }: { children: React.ReactNode }) {
  const { isLoggedIn } = useAuth();
  if (isLoggedIn) return <Navigate to="/vehicles" replace />;
  return <>{children}</>;
}

export default function App() {
  return (
    <>
      <Routes>
        <Route path="/" element={<Navigate to="/vehicles" replace />} />
        <Route
          path="/login"
          element={
            <RedirectIfAuthed>
              <LoginView />
            </RedirectIfAuthed>
          }
        />
        <Route
          element={
            <RequireAuth>
              <AppLayout />
            </RequireAuth>
          }
        >
          <Route path="/vehicles" element={<VehicleListView />} />
          <Route path="/dashboard" element={<DashboardView />} />
          <Route path="/alerts" element={<AlertsView />} />
          <Route path="/trajectory" element={<TrajectoryPlaybackView />} />
          <Route path="/geofences" element={<GeofenceListView />} />
        </Route>
        <Route path="*" element={<Navigate to="/vehicles" replace />} />
      </Routes>
      <ToastContainer />
    </>
  );
}
