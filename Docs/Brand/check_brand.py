#!/usr/bin/env python3
"""Check the engineering brand mirror and explicit PR assessment, not full UX."""
from __future__ import annotations
import argparse,json,re,sys
from pathlib import Path

def ratio(a: str,b: str) -> float:
    def lum(s: str) -> float:
        v=[int(s[i:i+2],16)/255 for i in (1,3,5)]
        return sum((x/12.92 if x<=.04045 else ((x+.055)/1.055)**2.4)*w for x,w in zip(v,(.2126,.7152,.0722)))
    x,y=sorted((lum(a),lum(b)));return (y+.05)/(x+.05)

def verify(root: Path,event: dict | None=None) -> None:
    latest=json.loads((root/'LATEST.json').read_text(encoding='utf-8'))
    version=latest['version']
    def member(name: str) -> Path:
        path=(root/name).resolve()
        if not path.is_relative_to(root.resolve()):raise ValueError('Brand pointer leaves its directory')
        return path
    tokens=json.loads(member(latest['tokens']).read_text(encoding='utf-8'))
    standard=member(latest['standard']).read_text(encoding='utf-8')
    if tokens['_meta']['version']!=version:raise ValueError('Token version does not match current brand pointer')
    if f'Version {version}' not in standard:raise ValueError('Standard version does not match current brand pointer')
    c=tokens['color']
    for key,value in c['brand'].items():
        if value not in standard:raise ValueError(f'Brand anchor absent from standard: {key}')
    pairs=[(c['text']['body'],c['surface']['card'],4.5),(c['text']['muted'],c['surface']['page'],4.5),(c['text']['inverse'],c['action']['primary'],4.5),(c['border']['control'],c['surface']['page'],3)]
    pairs += [(c['status'][s],c['surface'][s],4.5) for s in ('success','warning','error','info')]
    if any(ratio(a,b)<minimum for a,b,minimum in pairs):raise ValueError('Approved reference contrast pair failed')
    if tokens['targetPx']['preferredMinimum']<44:raise ValueError('Preferred control target is below the brand baseline')
    if event and 'pull_request' in event:
        body=event['pull_request'].get('body') or ''
        match=re.search(r'^Brand version:\s*([^\r\n]+)$',body,re.M|re.I)
        if not match or match.group(1).strip()!=version:raise ValueError(f'PR must include Brand version: {version}')
        impact=re.search(r'^Brand impact:\s*([^\r\n]+)$',body,re.M|re.I)
        if not impact or len(impact.group(1).strip())<12 or '[' in impact.group(1):raise ValueError('PR must describe Brand impact or explain why it is not applicable')

def main() -> int:
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--root',type=Path,default=Path(__file__).resolve().parent);parser.add_argument('--event',type=Path);args=parser.parse_args()
    try:
        event=json.loads(args.event.read_text(encoding='utf-8')) if args.event else None
        verify(args.root,event)
    except (OSError,ValueError,KeyError,TypeError) as error:
        print(f'Brand check failed: {error}',file=sys.stderr);return 1
    print('Brand mirror and applicable PR assessment checks passed. Full UX and release review remain required.');return 0
if __name__=='__main__':raise SystemExit(main())
