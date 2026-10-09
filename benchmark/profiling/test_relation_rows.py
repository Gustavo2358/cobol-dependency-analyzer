import importlib.util,struct,tempfile,unittest
from pathlib import Path
spec=importlib.util.spec_from_file_location('rows',Path(__file__).with_name('analyze-relation-rows.py'));rows=importlib.util.module_from_spec(spec);spec.loader.exec_module(rows)
class Relations(unittest.TestCase):
    def census(self,maps):
        with tempfile.TemporaryDirectory() as name:
            p=Path(name)/'rows.bin'
            with p.open('wb') as f:
                f.write(struct.pack('>iii',len(maps),10000,10000))
                for m in maps:
                    f.write(struct.pack('>i',len(m)))
                    for k,v in sorted(m.items()):f.write(struct.pack('>Q',(k<<32)|v))
            return rows.census(p)
    def test_equal_rows_share_a_complete_root(self):
        r=self.census([{i:1 for i in range(100)}]*10)
        self.assertEqual(1000,r['entries']);self.assertEqual(1,r['uniqueWholeRows']);self.assertEqual(100,r['factoredGroups']);self.assertEqual(1,r['uniqueMembershipMasks']);self.assertEqual(100,r['hamtShape']['uniqueInlineEntries']);self.assertTrue(r['roundTripExact'])
    def test_partial_difference_preserves_absence_and_value(self):
        r=self.census([{1:1},{2:1},{1:2,2:1}])
        self.assertEqual(4,r['entries']);self.assertEqual(3,r['factoredGroups']);self.assertEqual(3,r['uniqueWholeRows']);self.assertTrue(r['roundTripExact'])
    def test_a_boundary_exception_remains_separate(self):
        r=self.census([{i:1 for i in range(100)},{i:2 if i==50 else 1 for i in range(100)}])
        self.assertEqual(101,r['factoredGroups']);self.assertEqual(1,r['totalDeltaEntries']);self.assertEqual(2,r['hamtShape']['uniqueRoots']);self.assertTrue(r['roundTripExact'])
    def test_distinct_payloads_do_not_gain_semantic_compression(self):
        r=self.census([{k:context*100+k+1 for k in range(100)} for context in range(10)])
        self.assertEqual(1000,r['factoredGroups']);self.assertEqual(1000,r['hamtShape']['uniqueInlineEntries']);self.assertTrue(r['roundTripExact'])
    def test_empty_rows_are_distinct_from_reached_unknown_payload(self):
        r=self.census([{}, {1:1}, {}]);self.assertEqual(2,r['uniqueWholeRows']);self.assertEqual(1,r['factoredGroups']);self.assertTrue(r['roundTripExact'])
if __name__=='__main__':unittest.main()
