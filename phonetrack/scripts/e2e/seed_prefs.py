#!/usr/bin/env python3
"""Prints a phonetrack_prefs.xml for key=type:value arguments (types: s string, b boolean, i int, l long)."""
import sys
from xml.sax.saxutils import escape, quoteattr

lines = ['<?xml version="1.0" encoding="utf-8" standalone="yes" ?>', '<map>']
for arg in sys.argv[1:]:
    key, rest = arg.split('=', 1)
    kind, value = rest.split(':', 1)
    if kind == 's':
        lines.append(f'    <string name={quoteattr(key)}>{escape(value)}</string>')
    else:
        tag = {'b': 'boolean', 'i': 'int', 'l': 'long'}[kind]
        lines.append(f'    <{tag} name={quoteattr(key)} value={quoteattr(value)} />')
lines.append('</map>')
print('\n'.join(lines))
