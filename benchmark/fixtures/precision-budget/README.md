# Testemunhos de limites de precisão

Fixtures de discovery, em formato fixo. Não são um modo aproximado do produto.
`expected.json` registra nomes conhecidos, presença de DYNAMIC_REMAINDER e
SHA-256 de cada fonte. Reproduza com `benchmark/generate-precision-budgets.py`
em um diretório novo; não regenere o oráculo com a saída do analisador.

- `depth-12/32`: PERFORMs aninhados levam a DEEPCALL e voltam até AFTER.
  Qualquer corte de exploração pode perder ambas as dependências.
- `caller-correlation`: ONLY é observado depois do primeiro chamador; UNOBS,
  escrito pelo segundo, nunca é consumido. Fundir entradas pode inventar UNOBS.
- `caller-many-12/24`: o último valor escrito nunca é observado. Expõe o mesmo
  problema após exceder um orçamento maior de contextos precisos.
- `conditional-passthrough`: o retorno sem escrita conserva KEEP; o outro
  retorna NEW. POISON pertence a uma continuação lexical não executada.
- `full-unknown-write`: a escrita completa desconhecida elimina OLD e conserva
  um diagnóstico de resto desconhecido.
- `section-escape`: EXIT SECTION conserva GOOD e não executa BAD.
- `terminal-no-resume`: GOBACK permite REACHED e impede UNREACH no chamador.

A família de caixinhas continua gerada por `generate-hub-dispatch.py`, nos
modos return-fanout e return-fanout-fixed. O runner de precisão também produz
uma variante com CALL dinâmico antes da escrita do alvo. Os comparadores examinam
JSON completo e diagnósticos, além dos nomes: CALLs literais sozinhos são um
oráculo fraco para projeções de estado e correlação.

Veja [o discovery](../../precision-budget-discovery-20261009.md) para resultados,
limites, pins, comandos e os contraexemplos esperados RED.
