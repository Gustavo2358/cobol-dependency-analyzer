"""Normalize only published dependency names, across legacy and minimal schemas."""
import json, subprocess

def load(path):
 return json.loads(subprocess.check_output(['zstd','-dcq',str(path)])) if path.suffix=='.zst' else json.loads(path.read_text())

def reference(d):
 values=set()
 for site in d['dependencies']['programs']:
  values.update(('program',c['referenceName'].rstrip()) for c in site['candidates'])
 for site in d.get('sourceQualifiedDependencies',{}).get('occurrences',[]):
  values.update(('program',c.get('referenceName',c.get('rawValue','')).rstrip()) for c in site['candidates'])
 for item in d['fileDependencies']['declarations']:
  if item['namespace']=='cobol.external-file-name' and item['targetKind']=='LITERAL': values.add(('file',item['name']))
 for site in d['fileDependencies']['sites']:
  if site['namespace'] in ('cobol.external-file-name','cics.file'):values.update(('file',c['referenceName'].rstrip()) for c in site['candidates'])
 for item in d['sourceDependencies']['dependencies']:
  kind={'COPYBOOK':'copybook','SQL_INCLUDE':'sql-include','DCLGEN':'dclgen','DB2_TABLE':'db2-table'}[item['kind']]
  values.add((kind,(item.get('qualification','')+'.' if item.get('qualification') else '')+item['name']))
 return values

def product(d):
 programs=d if isinstance(d,list) else [d]
 return {(x["type"],x["name"]) for p in programs for x in p["dependencies"]}
