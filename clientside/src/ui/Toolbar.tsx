import React from 'react';

/** Horizontal control strip. Use <Toolbar.Spacer/> to push items right. */
export function Toolbar({
  children,
  className = '',
}: {
  children: React.ReactNode;
  className?: string;
}): React.ReactElement {
  return <div className={`ios-toolbar ${className}`.trim()}>{children}</div>;
}

Toolbar.Spacer = function ToolbarSpacer(): React.ReactElement {
  return <span className="ios-toolbar__spacer" />;
};
