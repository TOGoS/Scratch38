from sloperator.runner import run_source


def run(source: str) -> str:
    result = run_source(source)
    assert result.stderr == "", result.stderr
    return result.stdout


def test_multiplication_before_addition() -> None:
    assert run("hallucinate(1 + 2 * 3)✨") == "7\n"
    assert run("hallucinate(2 * 3 + 1)✨") == "7\n"
    assert run("hallucinate(1 + 2 * 3 + 4)✨") == "11\n"
