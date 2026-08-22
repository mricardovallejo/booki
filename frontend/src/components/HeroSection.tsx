import { Link } from 'react-router-dom';
import { ROUTES } from '../config/routes';

interface Props {
  onUploadClick?: () => void;
  hasDocuments?: boolean;
}

export default function HeroSection({ onUploadClick, hasDocuments }: Props) {
  return (
    <section className="relative h-[70vh] min-h-[480px] w-full overflow-hidden">
      <div className="absolute inset-0 bg-gradient-to-br from-indigo-950 via-slate-900 to-black" />
      <div className="absolute inset-0 bg-[url('https://images.unsplash.com/photo-1507842217121-9e9f147d7121?auto=format&fit=crop&w=1600&q=80')] bg-cover bg-center opacity-20" />
      <div className="absolute inset-0 bg-gradient-to-t from-booki-bg via-booki-bg/40 to-transparent" />
      <div className="absolute inset-0 bg-gradient-to-r from-booki-bg via-booki-bg/60 to-transparent" />

      <div className="relative mx-auto flex h-full max-w-7xl flex-col justify-end px-6 pb-20 pt-32">
        <span className="mb-3 w-fit rounded bg-white/10 px-3 py-1 text-xs font-semibold uppercase tracking-wider text-white/80 backdrop-blur">
          Smart reader
        </span>
        <h1 className="max-w-3xl text-4xl font-black leading-tight text-white text-shadow md:text-6xl">
          Read, discuss, and learn with <span className="text-booki-accent">BooKI</span>
        </h1>
        <p className="mt-4 max-w-2xl text-base leading-relaxed text-white/80 md:text-lg">
          Upload your PDFs, create sessions over a page range, and let the assistant explain, quiz, and discuss the content with you by text or voice.
        </p>
        <div className="mt-8 flex flex-wrap gap-4">
          {hasDocuments ? (
            <Link
              to={ROUTES.library}
              className="flex items-center gap-2 rounded bg-booki-accent px-6 py-3 text-sm font-bold text-white shadow-lg transition hover:bg-booki-accent-hover"
            >
              <svg className="h-5 w-5" fill="none" stroke="currentColor" strokeWidth="2" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" d="M4 6h16M4 12h16M4 18h16" />
              </svg>
              Browse your library
            </Link>
          ) : (
            <button
              onClick={onUploadClick}
              className="flex items-center gap-2 rounded bg-booki-accent px-6 py-3 text-sm font-bold text-white shadow-lg transition hover:bg-booki-accent-hover"
            >
              <svg className="h-5 w-5" fill="none" stroke="currentColor" strokeWidth="2" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" d="M12 6v6m0 0v6m0-6h6m-6 0H6" />
              </svg>
              Upload my first PDF
            </button>
          )}
          <button className="flex items-center gap-2 rounded bg-white/10 px-6 py-3 text-sm font-bold text-white backdrop-blur transition hover:bg-white/20">
            <svg className="h-5 w-5" fill="none" stroke="currentColor" strokeWidth="2" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" d="M13 16h-1v-4h-1m1-4h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z" />
            </svg>
            Learn more
          </button>
        </div>
      </div>
    </section>
  );
}
