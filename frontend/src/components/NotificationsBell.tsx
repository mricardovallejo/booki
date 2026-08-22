import { useState } from 'react';
import { useNotifications } from '../hooks/useNotifications';

interface Props {
  sessionId: number;
  refreshKey: number;
}

export default function NotificationsBell({ sessionId, refreshKey }: Props) {
  const notifications = useNotifications(sessionId, refreshKey);
  const [open, setOpen] = useState(false);

  return (
    <div className="relative">
      <button
        onClick={() => setOpen((o) => !o)}
        className="relative rounded-full bg-white/5 p-2 text-white/80 transition hover:bg-white/10 hover:text-white"
        title="Notifications"
      >
        <svg className="h-4 w-4" fill="none" stroke="currentColor" strokeWidth="2" viewBox="0 0 24 24">
          <path strokeLinecap="round" strokeLinejoin="round" d="M15 17h5l-1.4-1.4A2 2 0 0118 14.2V11a6 6 0 10-12 0v3.2c0 .5-.2 1-.6 1.4L4 17h5m6 0v1a3 3 0 11-6 0v-1m6 0H9" />
        </svg>
        {notifications.length > 0 && (
          <span className="absolute -right-0.5 -top-0.5 flex h-4 w-4 items-center justify-center rounded-full bg-booki-accent text-[10px] font-bold text-white">
            {notifications.length}
          </span>
        )}
      </button>
      {open && (
        <div className="absolute right-0 top-full z-20 mt-2 w-72 rounded-lg bg-booki-surface py-2 shadow-2xl ring-1 ring-white/10">
          <p className="px-4 py-1.5 text-xs font-semibold uppercase tracking-wide text-booki-muted">
            Notifications
          </p>
          {notifications.length === 0 ? (
            <p className="px-4 py-3 text-sm text-booki-muted">You're all caught up.</p>
          ) : (
            notifications.map((n) => (
              <div key={n.id} className="px-4 py-2 text-sm text-white/90">
                {n.message}
              </div>
            ))
          )}
        </div>
      )}
    </div>
  );
}
