import { gradientFor, initials } from '../lib/cover';
import type { Document } from '../types';

interface Props {
  document: Document;
  onSelect?: (doc: Document) => void;
  onOrganize?: (doc: Document) => void;
  onDelete?: (doc: Document) => void;
}

export default function DocumentCard({ document, onSelect, onOrganize, onDelete }: Props) {
  const gradient = gradientFor(document.title, document.id);

  return (
    <button
      onClick={() => onSelect?.(document)}
      className="group relative flex aspect-[2/3] w-40 min-w-[160px] flex-col overflow-hidden rounded-lg bg-booki-card text-left shadow-lg transition-all duration-300 hover:-translate-y-2 hover:shadow-glow sm:w-48 sm:min-w-[192px]"
    >
      <div className={`absolute inset-0 bg-gradient-to-br ${gradient} opacity-90`} />
      <div className="absolute inset-0 bg-gradient-to-t from-black/80 via-transparent to-transparent" />
      <div className="relative flex h-full flex-col items-center justify-center p-4 text-center">
        <span className="text-4xl font-black text-white/30 drop-shadow-lg">{initials(document.title)}</span>
      </div>
      {onOrganize && (
        <span
          role="button"
          tabIndex={0}
          onClick={(e) => {
            e.stopPropagation();
            onOrganize(document);
          }}
          onKeyDown={(e) => {
            if (e.key === 'Enter' || e.key === ' ') {
              e.stopPropagation();
              onOrganize(document);
            }
          }}
          className="absolute right-2 top-2 z-10 rounded-full bg-black/50 p-1.5 text-white/80 opacity-0 backdrop-blur transition hover:bg-black/70 hover:text-white group-hover:opacity-100"
          title="Organize tags"
        >
          <svg className="h-3.5 w-3.5" fill="none" stroke="currentColor" strokeWidth="2" viewBox="0 0 24 24">
            <path strokeLinecap="round" strokeLinejoin="round" d="M7 7h.01M7 3h5.586a1 1 0 01.707.293l7.414 7.414a1 1 0 010 1.414l-6.586 6.586a1 1 0 01-1.414 0L5.293 11.293A1 1 0 015 10.586V5a2 2 0 012-2z" />
          </svg>
        </span>
      )}
      {onDelete && (
        <span
          role="button"
          tabIndex={0}
          onClick={(e) => {
            e.stopPropagation();
            onDelete(document);
          }}
          onKeyDown={(e) => {
            if (e.key === 'Enter' || e.key === ' ') {
              e.stopPropagation();
              onDelete(document);
            }
          }}
          className="absolute left-2 top-2 z-10 rounded-full bg-black/50 p-1.5 text-white/80 opacity-0 backdrop-blur transition hover:bg-black/70 hover:text-rose-300 group-hover:opacity-100"
          title="Remove book"
        >
          <svg className="h-3.5 w-3.5" fill="none" stroke="currentColor" strokeWidth="2" viewBox="0 0 24 24">
            <path strokeLinecap="round" strokeLinejoin="round" d="M6 7h12M9 7V5a1 1 0 011-1h4a1 1 0 011 1v2m2 0l-.7 11.2a2 2 0 01-2 1.8H8.7a2 2 0 01-2-1.8L6 7h12z" />
          </svg>
        </span>
      )}
      <div className="absolute bottom-0 w-full p-3">
        <h3 className="line-clamp-2 text-sm font-bold leading-tight text-white drop-shadow-md">
          {document.title}
        </h3>
        <p className="mt-1 text-xs text-white/70">{document.pageCount} pages</p>
      </div>
      <div className="absolute inset-0 flex items-center justify-center bg-black/60 opacity-0 transition-opacity group-hover:opacity-100">
        <span className="rounded-full bg-booki-accent px-4 py-2 text-sm font-bold text-white shadow-lg">
          Open
        </span>
      </div>
    </button>
  );
}
