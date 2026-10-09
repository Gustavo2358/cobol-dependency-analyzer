#!/usr/bin/env python3
"""Finite text-fit composition law used by backward MOVE goals."""
from itertools import product

def main():
 cases=0
 for text in ('','A','ABCDEFGH','A B C'):
  for widths in product((1,2,4,8),repeat=4):
   exact=text
   for w in widths:exact=exact[:w]+' '*max(0,w-len(exact))
   prefix=min(widths);length=widths[-1]
   composed=text[:prefix]+' '*max(0,length-min(len(text),prefix))
   assert exact==composed,(text,widths,exact,composed)
   cases+=1
 print(f'{cases} text-fit compositions agree')
if __name__=='__main__':main()
