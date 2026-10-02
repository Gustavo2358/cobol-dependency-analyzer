"""Naming guard: canonical repository references in docs are not product names."""
from pathlib import Path
import os,shutil,subprocess,tempfile,unittest

ROOT=Path(__file__).resolve().parents[2]
VENDOR='pro'+'leap'
PURPOSE='bench'+'mark'

class NamingGuard(unittest.TestCase):
    def run_guard(self,name,content,without_rg=False):
        with tempfile.TemporaryDirectory() as directory:
            root=Path(directory);(root/'scripts').mkdir()
            shutil.copyfile(ROOT/'scripts/verify-naming.sh',root/'scripts/verify-naming.sh')
            subprocess.run(['git','init','-q',str(root)],check=True)
            path=root/name;path.parent.mkdir(parents=True,exist_ok=True);path.write_text(content)
            env=os.environ.copy()
            if without_rg:
                tools=root/'tools';tools.mkdir()
                for command in ('bash','git','dirname','sed','python3'):
                    (tools/command).symlink_to(shutil.which(command))
                env['PATH']=str(tools)
            return subprocess.run(['bash','scripts/verify-naming.sh'],cwd=root,env=env,capture_output=True,text=True).returncode
    def test_canonical_repository_in_docs(self):
        self.assertEqual(0,self.run_guard('docs/evidence.md','https://github.com/example/'+VENDOR+'-poc/pull/1'))
    def test_canonical_repository_in_json_evidence(self):
        self.assertEqual(0,self.run_guard('docs/work/evidence.json',
                '{"sources":{"'+VENDOR+'-poc":"abc123"}}'))
    def test_json_evidence_does_not_exempt_other_legacy_names(self):
        for text in (VENDOR+' engine',VENDOR+'-pocket',VENDOR+'-poc-engine',PURPOSE):
            with self.subTest(text=text):
                self.assertNotEqual(0,self.run_guard('docs/work/evidence.json',
                        '{"repository":"'+VENDOR+'-poc","label":"'+text+'"}'))
    def test_json_product_resource_cannot_use_repository_exception(self):
        self.assertNotEqual(0,self.run_guard('src/resource.json','{"name":"'+VENDOR+'-poc"}'))
    def test_mixed_doc_still_rejects_product_identifier(self):
        self.assertNotEqual(0,self.run_guard('docs/evidence.md',VENDOR+'-poc\n'+VENDOR+' engine'))
    def test_product_code_cannot_use_repository_exception(self):
        self.assertNotEqual(0,self.run_guard('src/app.java',VENDOR+'-poc'))
    def test_similar_identifier_is_not_the_canonical_repository(self):
        self.assertNotEqual(0,self.run_guard('docs/product.md',VENDOR+'-pocket'))
    def test_measurement_term_in_markdown(self):
        for text in (PURPOSE, PURPOSE+'s', PURPOSE+'ing', PURPOSE+'.', PURPOSE+'s,', 'A '+PURPOSE.upper()+' measures parsing time.'):
            with self.subTest(text=text):
                self.assertEqual(0,self.run_guard('docs/work/performance.md',text))
    def test_measurement_exception_does_not_hide_product_identifiers(self):
        for text in ('cobol-'+PURPOSE, PURPOSE+'Runner', PURPOSE+'_engine', PURPOSE+'.App',
                     VENDOR+' engine with '+PURPOSE+'s'):
            with self.subTest(text=text):
                self.assertNotEqual(0,self.run_guard('docs/product.md',text))
    def test_purpose_in_code_and_machine_readable_labels_stays_forbidden(self):
        for name,text in (('src/App.java',PURPOSE),
                          ('docs/work/evidence.yaml','title: '+PURPOSE),
                          ('docs/work/evidence.json','{"label":"'+PURPOSE+'"}')):
            with self.subTest(name=name):
                self.assertNotEqual(0,self.run_guard(name,text))
    def test_canonical_repository_in_yaml_evidence(self):
        for suffix in ('yaml','yml'):
            with self.subTest(suffix=suffix):
                self.assertEqual(0,self.run_guard('docs/work/evidence.'+suffix,
                    'pr: https://github.com/example/'+VENDOR+'-poc/pull/80'))
    def test_yaml_repository_exception_is_exact_and_documentary(self):
        for name,text in (('docs/work/evidence.yaml','repository: '+VENDOR+'-pocket'),
                          ('docs/work/evidence.yml','repository: '+VENDOR+'-poc\nname: '+VENDOR+' engine'),
                          ('src/resource.yaml','repository: '+VENDOR+'-poc')):
            with self.subTest(name=name,text=text):
                self.assertNotEqual(0,self.run_guard(name,text))
    def test_historical_artifact_references_in_markdown(self):
        for artifact in ('.'+VENDOR+'-run-20261001/evidence/', PURPOSE+'-summary.json',
                         PURPOSE+'s/', '/tmp/'+VENDOR+'/report.json'):
            with self.subTest(artifact=artifact):
                self.assertEqual(0,self.run_guard('docs/work/report.md','Evidence: `'+artifact+'`'))
    def test_artifact_reference_does_not_exempt_unrelated_prose(self):
        for text in ('`'+VENDOR+' engine`', '`'+VENDOR+'`', '`'+VENDOR+'.Engine`', '`'+PURPOSE+'.App`',
                     '`'+VENDOR+'-summary.json` '+VENDOR+' engine',
                     '`some/'+VENDOR+' engine/`'):
            with self.subTest(text=text):
                self.assertNotEqual(0,self.run_guard('docs/work/report.md',text))
    def test_artifact_exceptions_do_not_apply_to_product_files(self):
        self.assertNotEqual(0,self.run_guard('src/app.py','path = "'+VENDOR+'-summary.json"'))
        self.assertNotEqual(0,self.run_guard('src/'+PURPOSE+'-summary.json','{}'))
    def test_real_documentary_shapes(self):
        text=('The '+PURPOSE+'s contain 80 runs.\n'
              'Evidence in `.'+VENDOR+'-post-antlr-implementation-20261001/evidence/`.\n'
              '- `'+PURPOSE+'-summary.json` and `'+PURPOSE+'s/`: measurements.\n')
        self.assertEqual(0,self.run_guard('docs/work/performance.md',text,without_rg=True))
    def test_old_path_remains_forbidden(self):
        self.assertNotEqual(0,self.run_guard('src/'+VENDOR+'.txt','source'))
    def test_content_guard_does_not_silently_pass_without_ripgrep(self):
        self.assertNotEqual(0,self.run_guard('src/app.java',VENDOR+' engine',without_rg=True))
    def test_canonical_repository_remains_valid_without_ripgrep(self):
        self.assertEqual(0,self.run_guard('docs/evidence.md',VENDOR+'-poc',without_rg=True))

if __name__=='__main__':unittest.main()
