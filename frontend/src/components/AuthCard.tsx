import type { ReactNode } from 'react';
import Logo from './Logo';

/** The centered card shared by the signed-out screens. */
export default function AuthCard({ children }: { children: ReactNode }) {
  return (
    <div className="relative flex min-h-screen items-center justify-center overflow-hidden bg-booki-bg px-4">
      <div className="absolute inset-0 bg-gradient-to-br from-indigo-950 via-slate-900 to-black" />
      <div className="absolute inset-0 bg-gradient-to-t from-booki-bg via-booki-bg/60 to-transparent" />

      <div className="relative w-full max-w-md rounded-2xl bg-booki-surface/90 p-8 shadow-2xl backdrop-blur">
        <Logo size="lg" />
        {children}
      </div>
    </div>
  );
}
