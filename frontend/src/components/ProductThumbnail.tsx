import React, { useState } from 'react';
import { Cpu, HardDrive, Layers, Server } from 'lucide-react';

export interface ProductThumbnailProps {
  src?: string | null;
  alt: string;
  categoryName?: string;
  sku?: string;
  className?: string;
  aspectRatio?: 'square' | 'video' | 'auto';
  height?: string | number;
}

export const ProductThumbnail: React.FC<ProductThumbnailProps> = ({
  src,
  alt,
  categoryName,
  sku,
  className = '',
  aspectRatio = 'square',
  height,
}) => {
  const [hasError, setHasError] = useState(false);
  const [isLoaded, setIsLoaded] = useState(false);

  // Reset error state if image source changes
  React.useEffect(() => {
    setHasError(false);
    setIsLoaded(false);
  }, [src]);

  // Determine category-specific hardware icon
  const renderHardwareIcon = () => {
    const cat = (categoryName || '').toLowerCase();
    if (cat.includes('cpu') || cat.includes('processor')) {
      return <Cpu size={28} className="hardware-icon" />;
    }
    if (cat.includes('gpu') || cat.includes('card') || cat.includes('graphic')) {
      return <Layers size={28} className="hardware-icon" />;
    }
    if (cat.includes('storage') || cat.includes('ssd') || cat.includes('drive')) {
      return <HardDrive size={28} className="hardware-icon" />;
    }
    return <Server size={28} className="hardware-icon" />;
  };

  const showFallback = !src || hasError;

  return (
    <div
      className={`product-thumbnail-container ${aspectRatio} ${className}`}
      style={height ? { height } : undefined}
      role="img"
      aria-label={alt}
    >
      {!showFallback && (
        <img
          src={src}
          alt={alt}
          className={`product-thumbnail-img ${isLoaded ? 'loaded' : 'loading'}`}
          onLoad={() => setIsLoaded(true)}
          onError={() => setHasError(true)}
          loading="lazy"
        />
      )}

      {showFallback && (
        <div className="product-thumbnail-fallback">
          {/* Hardware Circuit Grid SVG Background */}
          <svg
            className="circuit-svg-bg"
            viewBox="0 0 200 200"
            fill="none"
            xmlns="http://www.w3.org/2000/svg"
            aria-hidden="true"
          >
            <defs>
              <pattern id="circuit-grid" width="20" height="20" patternUnits="userSpaceOnUse">
                <path d="M 20 0 L 0 0 0 20" fill="none" stroke="rgba(56, 189, 248, 0.07)" strokeWidth="1" />
              </pattern>
              <linearGradient id="chip-glow" x1="0%" y1="0%" x2="100%" y2="100%">
                <stop offset="0%" stopColor="#38bdf8" stopOpacity="0.2" />
                <stop offset="100%" stopColor="#3b82f6" stopOpacity="0.05" />
              </linearGradient>
            </defs>
            <rect width="100%" height="100%" fill="url(#circuit-grid)" />
            {/* Architectural circuit traces */}
            <path
              d="M 20 100 H 65 L 85 80 H 135"
              stroke="rgba(56, 189, 248, 0.25)"
              strokeWidth="1.5"
              strokeDasharray="4 2"
            />
            <path
              d="M 180 100 H 135 L 115 120 H 65"
              stroke="rgba(99, 102, 241, 0.25)"
              strokeWidth="1.5"
              strokeDasharray="4 2"
            />
            <circle cx="85" cy="80" r="3" fill="#38bdf8" fillOpacity="0.6" />
            <circle cx="115" cy="120" r="3" fill="#6366f1" fillOpacity="0.6" />
            {/* Silicon die center */}
            <rect
              x="60"
              y="60"
              width="80"
              height="80"
              rx="12"
              fill="url(#chip-glow)"
              stroke="rgba(56, 189, 248, 0.3)"
              strokeWidth="1.2"
            />
          </svg>

          {/* Central Hardware Indicator */}
          <div className="fallback-content">
            <div className="icon-wrapper">
              {renderHardwareIcon()}
            </div>
            <div className="fallback-text">
              <span className="fallback-category">{categoryName || 'Hardware Component'}</span>
              {sku && <span className="fallback-sku">{sku}</span>}
            </div>
          </div>
        </div>
      )}
    </div>
  );
};

