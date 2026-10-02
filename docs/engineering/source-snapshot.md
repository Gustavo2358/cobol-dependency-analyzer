# Snapshot de fontes e evidência histórica

Os bundles, probes, cópias de referências e ferramentas do discovery D0 foram
retirados da árvore atual; os relatórios e resumos permanecem. O
[índice histórico](../history/carddemo-architectural-reassessment-d0-20260923/README.md)
explica como recuperar e auditar a publicação original pelo Git.

Os diretórios gerados `dist/`, `dist-cbstm03a/` e `dist-cbstm03d/` também foram
retirados. Os comandos do README os regeneram, usando a interface original de
`src/main/resources/web/`. Corpus, fixtures e goldens atuais foram preservados,
incluindo os utilizados em `docs/work/evidence/`.

A remoção reduz o snapshot da main. Um clone com histórico completo ainda contém
os blobs antigos; o histórico Git não foi reescrito.

## Qualificação da limpeza

O gate `python3 -B scripts/harness/lean.py fast` passou nos três repositórios
em 2026-10-02, sem mudanças nos testes ou gates. Código, recursos de teste,
baselines executáveis, dependências e pins foram preservados.

A integração executou novamente o runtime anterior e os checkouts limpos:
CardDemo COACTUPC, `02_if_join` e `cobol/goto/if-jump.cbl`, cada um em JSON puro
e Zstandard, percorrendo frontend → lower → CFG → dependências. As 48 execuções
CLI terminaram com sucesso. Os 144 pares de arquivos de saída foram idênticos
byte a byte, incluindo os produtos JSON e os arquivos de interface regenerados.
Isso qualifica a limpeza nesses casos; não constitui uma nova execução do corpus
completo. Os PRs registram os commits finais e o resultado do FAST remoto.
