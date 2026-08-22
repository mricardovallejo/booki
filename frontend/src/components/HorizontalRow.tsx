import { useRef } from 'react';
import DocumentCard from './DocumentCard';
import type { Document } from '../types';

interface Props {
  title: string;
  documents: Document[];
  onSelect?: (doc: Document) => void;
  onOrganize?: (doc: Document) => void;
  onDelete?: (doc: Document) => void;
}

export default function HorizontalRow({ title, documents, onSelect, onOrganize, onDelete }: Props) {
  const scrollRef = useRef<HTMLDivElement>(null);

  const scroll = (direction: 'left' | 'right') => {
    if (!scrollRef.current) return;
    const amount = direction === 'left' ? -400 : 400;
    scrollRef.current.scrollBy({ left: amount, behavior: 'smooth' });
  };

  if (documents.length === 0) return null;

  return (
    <section className="relative py-6">
      <div className="mx-auto max-w-7xl">
        <h2 className="mb-4 px-6 text-lg font-bold text-white md:text-xl">{title}</h2>
        <div className="group relative">
          <button
            onClick={() => scroll('left')}
            className="absolute left-0 top-0 z-10 h-full w-12 bg-gradient-to-r from-booki-bg to-transparent pl-2 text-white opacity-0 transition group-hover:opacity-100"
            aria-label="Previous"
          >
            <svg className="h-8 w-8" fill="none" stroke="currentColor" strokeWidth="2" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" d="M15 19l-7-7 7-7" />
            </svg>
          </button>
          <div
            ref={scrollRef}
            className="flex gap-4 overflow-x-auto px-6 pb-4 scrollbar-hide"
            style={{ scrollbarWidth: 'none', msOverflowStyle: 'none' }}
          >
            {documents.map((doc) => (
              <DocumentCard key={doc.id} document={doc} onSelect={onSelect} onOrganize={onOrganize} onDelete={onDelete} />
            ))}
          </div>
          <button
            onClick={() => scroll('right')}
            className="absolute right-0 top-0 z-10 h-full w-12 bg-gradient-to-l from-booki-bg to-transparent pr-2 text-right text-white opacity-0 transition group-hover:opacity-100"
            aria-label="Next"
          >
            <svg className="ml-auto h-8 w-8" fill="none" stroke="currentColor" strokeWidth="2" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" d="M9 5l7 7-7 7" />
            </svg>
          </button>
        </div>
      </div>
    </section>
  );
}
