#!/usr/bin/env python3
"""Exact post-hoc row factoring and canonical CHAMP-like shape census.

No production HAMT or solver is implemented. Shape counts are not JVM bytes,
execution timings or a proof that a batched solver preserves the semantics.
"""
import argparse,array,collections,hashlib,json,struct,sys
from pathlib import Path

def read(path):
    rows=[]
    with path.open('rb') as f:
        count,keys,values=struct.unpack('>iii',f.read(12))
        for _ in range(count):
            n=struct.unpack('>i',f.read(4))[0];row=array.array('Q');row.frombytes(f.read(8*n))
            if sys.byteorder=='little':row.byteswap()
            assert len(row)==n;assert all((row[i]>>32)<(row[i+1]>>32) for i in range(n-1));rows.append(row)
        assert not f.read(1)
    return rows,keys,values

def delta(a,b):
    i=j=n=0
    while i<len(a) and j<len(b):
        ka,kb=a[i]>>32,b[j]>>32
        if ka==kb:n+=a[i]!=b[j];i+=1;j+=1
        elif ka<kb:n+=1;i+=1
        else:n+=1;j+=1
    return n+len(a)-i+len(b)-j

def mix(key):
    x=key;x^=x>>16;x=(x*0x7feb352d)&0xffffffff;x^=x>>15;x=(x*0x846ca68b)&0xffffffff;x^=x>>16;return x

def shape(rows):
    intern={};node_entries=[];roots=[];expanded_nodes=expanded_entries=0
    def build(items,depth):
        nonlocal expanded_nodes,expanded_entries
        buckets=collections.defaultdict(list)
        for pair in items:buckets[(mix(pair>>32)>>(5*depth))&31].append(pair)
        inline=[];children=[];data_map=node_map=0
        for bucket,group in sorted(buckets.items()):
            if len(group)==1:data_map|=1<<bucket;inline.append(group[0])
            else:node_map|=1<<bucket;children.append(build(group,depth+1))
        signature=(depth,data_map,node_map,tuple(inline),tuple(children));found=intern.get(signature)
        if found is None:found=len(intern);intern[signature]=found;node_entries.append(len(inline))
        expanded_nodes+=1;expanded_entries+=len(inline);return found
    for row in rows:roots.append(build(row,0))
    return dict(uniqueRoots=len(set(roots)),expandedTrieNodes=expanded_nodes,uniqueTrieNodes=len(intern),expandedInlineEntries=expanded_entries,uniqueInlineEntries=sum(node_entries),representation='Canonical 32-way hash-prefix shape; ideal global hash-consing of all completed rows, stronger than ordinary persistence')

def census(path):
    rows,keys,values=read(path);entries=sum(map(len,rows));unique=collections.Counter(row.tobytes() for row in rows)
    reference=max(range(len(rows)),key=lambda i:(unique[rows[i].tobytes()],len(rows[i])))
    diffs=sorted(delta(rows[reference],r) for r in rows)
    # Invert into physical key -> exact value -> row membership. Count all pairs;
    # each row bit is retained and a replay must reproduce the exact original map.
    groups={}
    for rid,row in enumerate(rows):
        for pair in row:groups[pair]=groups.get(pair,0)|(1<<rid)
    masks=set(groups.values());mask_bits=sum(m.bit_length() for m in masks)
    restored=0
    for rid,row in enumerate(rows):
        expected=set(row);actual={pair for pair,mask in groups.items() if (mask>>rid)&1};assert actual==expected;restored+=len(actual)
    assert restored==entries
    return dict(file=str(path),sha256=hashlib.sha256(path.read_bytes()).hexdigest(),rows=len(rows),keys=keys,values=values,entries=entries,uniqueWholeRows=len(unique),wholeRowClasses=sorted([dict(entries=len(row)//8,contexts=count) for row,count in unique.items()],key=lambda x:(-x["contexts"],-x["entries"])),largestEqualRowClass=max(unique.values()),nonemptyRows=sum(bool(r) for r in rows),referenceRow=reference,referenceEntries=len(rows[reference]),totalDeltaEntries=sum(diffs),medianDeltaEntries=diffs[len(diffs)//2],maxDeltaEntries=max(diffs),factoredGroups=len(groups),uniqueMembershipMasks=len(masks),membershipMaskWords=sum((m.bit_length()+63)//64 for m in masks),roundTripExact=True,hamtShape=shape(rows))

def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('root',type=Path);p.add_argument('--output',type=Path,required=True);a=p.parse_args();reports=[]
    for path in sorted(a.root.rglob('before.bin')):
        directory=path.parent
        item={'case':str(directory.relative_to(a.root)).removeprefix('dump-')}
        for kind in ('before','control'):item[kind]=census(directory/(kind+'.bin'))
        reports.append(item);print(item['case'],{k:{f:item[k][f] for f in ['entries','uniqueWholeRows','medianDeltaEntries','factoredGroups','uniqueMembershipMasks']} for k in ('before','control')},flush=True)
        a.output.write_text(json.dumps(reports,indent=2)+'\n')
if __name__=='__main__':main()
