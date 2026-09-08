#!/usr/bin/env python3
import argparse
import re
import sys
import xml.etree.ElementTree as ET

BOUNDS = re.compile(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]")


def center(bounds: str) -> tuple[int, int]:
    match = BOUNDS.fullmatch(bounds or "")
    if not match:
        raise ValueError(f"invalid bounds: {bounds!r}")
    x1, y1, x2, y2 = map(int, match.groups())
    return (x1 + x2) // 2, (y1 + y2) // 2


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("xml")
    parser.add_argument("attribute", choices=("text", "content-desc"))
    parser.add_argument("value")
    parser.add_argument("--raw", action="store_true")
    parser.add_argument("--nearest-checkable", action="store_true")
    args = parser.parse_args()

    root = ET.parse(args.xml).getroot()
    parent = {child: node for node in root.iter() for child in node}
    matches = [
        node
        for node in root.iter("node")
        if node.attrib.get(args.attribute) == args.value
        and node.attrib.get("enabled", "true") == "true"
    ]
    if not matches:
        return 2

    node = matches[0]
    target = node
    if args.nearest_checkable:
        label_x, label_y = center(node.attrib.get("bounds", ""))
        candidates = []
        for candidate in root.iter("node"):
            if candidate.attrib.get("enabled", "true") != "true":
                continue
            if candidate.attrib.get("checkable") != "true":
                continue
            try:
                candidate_x, candidate_y = center(candidate.attrib.get("bounds", ""))
            except ValueError:
                continue
            candidates.append(
                (
                    abs(candidate_y - label_y),
                    abs(candidate_x - label_x),
                    candidate,
                )
            )
        if not candidates:
            return 3
        candidates.sort(key=lambda item: (item[0], item[1]))
        target = candidates[0][2]
    elif not args.raw:
        while target.attrib.get("clickable") != "true" and target in parent:
            target = parent[target]
        if target.attrib.get("clickable") != "true":
            target = node

    x, y = center(target.attrib.get("bounds", ""))
    print(f"{x} {y}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
