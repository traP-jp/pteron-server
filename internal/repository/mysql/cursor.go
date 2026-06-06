package mysql

import (
	"encoding/base64"
	"encoding/binary"
	"time"

	"github.com/traP-jp/pteron-server/internal/domain"
)

const paginationCursorSize = 24

type paginationCursor struct {
	CreatedAt time.Time
	ID        domain.ID
}

func encodeCursor(createdAt time.Time, id domain.ID) string {
	bytes := make([]byte, paginationCursorSize)
	binary.BigEndian.PutUint64(bytes[0:8], uint64(createdAt.UTC().UnixMilli()))
	copy(bytes[8:24], id.Bytes())
	return base64.RawURLEncoding.EncodeToString(bytes)
}

func decodeCursor(value string) (*paginationCursor, error) {
	bytes, err := base64.RawURLEncoding.DecodeString(value)
	if err != nil || len(bytes) != paginationCursorSize {
		return nil, err
	}
	epochMillis := int64(binary.BigEndian.Uint64(bytes[0:8]))
	id, err := domain.IDFromBytes(bytes[8:24])
	if err != nil {
		return nil, err
	}
	return &paginationCursor{
		CreatedAt: time.UnixMilli(epochMillis).UTC(),
		ID:        id,
	}, nil
}

func encodeRankingCursor(rank int64, id domain.ID) string {
	bytes := make([]byte, paginationCursorSize)
	binary.BigEndian.PutUint64(bytes[0:8], uint64(rank))
	copy(bytes[8:24], id.Bytes())
	return base64.RawURLEncoding.EncodeToString(bytes)
}

func decodeRankingCursor(value string) (int64, error) {
	bytes, err := base64.RawURLEncoding.DecodeString(value)
	if err != nil || len(bytes) != paginationCursorSize {
		return 0, err
	}
	return int64(binary.BigEndian.Uint64(bytes[0:8])), nil
}
