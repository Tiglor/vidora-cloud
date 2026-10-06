"""endpoint 注释形状自检（.code/coding-standards.md 3.1 第 2 条）。

默认查源码；加 --baked 查注解处理器烘出来的 `*__Javadoc.json` —— 那才是 springdoc 实际会当成
summary 的那一行，所以改完注释、`mvn -o clean compile` 之后再用 --baked 复验一次。

用法：
    python .code/tools/check-endpoint-javadoc.py            # 源码形状
    python .code/tools/check-endpoint-javadoc.py --baked    # 烘出来的 JSON
退出码 0 ＝ 全部合规；否则逐条打印 文件:行 方法名 与原因。
"""
import json
import re
import sys
from pathlib import Path

MAP_ANN = re.compile(r"^\s*@(Get|Post|Put|Delete|Patch)Mapping")
ANN = re.compile(r"^\s*@\w+")
SIG = re.compile(r"^[A-Za-z0-9_<>,.? \t]*\s(\w+)\s*\(")
PUNCT = "。：，；:,"
MAX_NAME = 14


def repo_root():
    # 本文件在 <仓库根>/.code/tools/ 下；按 pom.xml 定位，别硬算层数
    for cand in Path(__file__).resolve().parents:
        if (cand / "pom.xml").exists():
            return cand
    raise SystemExit("找不到仓库根（没有 pom.xml）")


ROOT = repo_root()


def strip_comment(raw):
    t = raw.strip()
    if t.startswith("/**"):
        t = t[3:]
    if t.endswith("*/"):
        t = t[:-2]
    t = t.strip()
    if t.startswith("*"):
        t = t[1:]
    return t.strip()


def collect_docs(lines):
    """{Javadoc 块的结束行号(1-based): (内容行, 是否单行紧凑式)}"""
    docs = {}
    i = 0
    while i < len(lines):
        if lines[i].strip().startswith("/**"):
            j = i
            while j < len(lines) and not lines[j].strip().endswith("*/"):
                j += 1
            if j >= len(lines):
                break
            body = [t for t in (strip_comment(x) for x in lines[i:j + 1]) if t and t != "*"]
            docs[j + 1] = (body, j == i)
            i = j + 1
            continue
        i += 1
    return docs


def filler(s):
    """mapping 与签名之间允许出现的行：别的注解、空行、// 说明。"""
    return (not s) or ANN.match(s) is not None or s.startswith("//")


def doc_above(docs, lines, idx):
    """mapping 注解往上找 Javadoc：中间只允许别的注解、空行和 // 说明。"""
    i = idx - 1
    while i >= 0:
        s = lines[i].strip()
        if s.endswith("*/") and (i + 1) in docs:
            return docs[i + 1]
        if not filler(s):
            return None
        i -= 1
    return None


def method_name(lines, idx):
    j = idx
    while j < len(lines) and filler(lines[j].strip()):
        j += 1
    m = SIG.match(lines[j].strip()) if j < len(lines) else None
    return m.group(1) if m else "??"


def endpoints(path):
    lines = path.read_text(encoding="utf-8").splitlines()
    docs = collect_docs(lines)
    return [(i + 1, method_name(lines, i), doc_above(docs, lines, i))
            for i, raw in enumerate(lines) if MAP_ANN.match(raw)]


def baked_docs(java_path):
    rel = java_path.as_posix()
    if "/src/main/java/" not in rel:
        return None
    js = Path(rel.replace("/src/main/java/", "/target/classes/")[:-5] + "__Javadoc.json")
    if not js.exists():
        return None
    data = json.loads(js.read_text(encoding="utf-8"))
    return {m["name"]: (m.get("doc") or "") for m in data.get("methods", [])}


def check_first(where, first, problems):
    if not first:
        problems.append(where + " 空注释块")
    elif first.startswith("@"):
        problems.append(where + " 没有接口名首行（第一行是 " + first[:24] + "）")
    elif any(c in first for c in PUNCT):
        problems.append(where + " 首行不像接口名：" + first)
    elif len(first) > MAX_NAME:
        problems.append(where + " 首行偏长（" + str(len(first)) + " 字）：" + first)


def main():
    baked = "--baked" in sys.argv[1:]
    problems, total = [], 0
    for f in sorted(p for p in ROOT.rglob("*Controller.java") if "/target/" not in p.as_posix()):
        eps = endpoints(f)
        total += len(eps)
        docs = baked_docs(f) if baked else None
        if baked and docs is None:
            problems.append(f.name + " 没有 __Javadoc.json（处理器没跑到，或没先 mvn -o clean compile）")
            continue
        for line_no, name, doc in eps:
            where = f.name + ":" + str(line_no) + " " + name
            if baked:
                if name not in docs:
                    problems.append(where + " JSON 里没有这个方法的 doc")
                    continue
                check_first(where, next((l.strip() for l in docs[name].splitlines() if l.strip()), ""), problems)
            elif doc is None:
                problems.append(where + " 缺 Javadoc，或贴在了注解与签名之间（必须放在 mapping 注解之上）")
            elif doc[1]:
                problems.append(where + " 用了禁止的单行紧凑式：" + (doc[0][0] if doc[0] else ""))
            else:
                check_first(where, doc[0][0] if doc[0] else "", problems)
    print("口径=" + ("烘出的 JSON" if baked else "源码") + "，endpoint " + str(total) + " 个，问题 " + str(len(problems)) + " 条")
    for p in problems:
        print("  " + p)
    return 1 if problems else 0


if __name__ == "__main__":
    sys.exit(main())
