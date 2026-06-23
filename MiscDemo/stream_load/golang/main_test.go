package main

import (
	"regexp"
	"strings"
	"testing"
)

func TestGenerateRandomIDFormat(t *testing.T) {
	id := generateRandomID()
	parts := strings.Split(id, "-")
	if len(parts) != 5 {
		t.Fatalf("expected 5 segments, got %d: %s", len(parts), id)
	}

	expectedLengths := []int{8, 4, 4, 4, 12}
	for i, part := range parts {
		if len(part) != expectedLengths[i] {
			t.Fatalf("segment %d expected len %d, got %d (%s)", i, expectedLengths[i], len(part), id)
		}
	}

	if !regexp.MustCompile(`^[0-9a-zA-Z-]+$`).MatchString(id) {
		t.Fatalf("id contains unexpected characters: %s", id)
	}
}
