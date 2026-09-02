import React from 'react';

export function usePageMetadata(title: string, description: string, canonicalPath: string): void {
  React.useEffect(() => {
    document.title = `${title} | Cito`;
    const descriptionElement = document.querySelector<HTMLMetaElement>('meta[name="description"]');
    const canonicalElement = document.querySelector<HTMLLinkElement>('link[rel="canonical"]');
    const previousDescription = descriptionElement?.content;
    const previousCanonical = canonicalElement?.href;
    if (descriptionElement) descriptionElement.content = description;
    if (canonicalElement) canonicalElement.href = `${window.location.origin}${canonicalPath}`;
    return () => {
      if (descriptionElement && previousDescription) descriptionElement.content = previousDescription;
      if (canonicalElement && previousCanonical) canonicalElement.href = previousCanonical;
    };
  }, [canonicalPath, description, title]);
}
