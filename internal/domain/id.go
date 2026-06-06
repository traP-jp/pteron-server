package domain

import (
	"database/sql/driver"
	"fmt"

	"github.com/google/uuid"
)

type ID uuid.UUID

func NewID() (ID, error) {
	id, err := uuid.NewV7()
	return ID(id), err
}

func MustNewID() ID {
	id, err := NewID()
	if err != nil {
		panic(err)
	}
	return id
}

func ParseID(value string) (ID, error) {
	id, err := uuid.Parse(value)
	return ID(id), err
}

func IDFromBytes(value []byte) (ID, error) {
	id, err := uuid.FromBytes(value)
	return ID(id), err
}

func (id ID) UUID() uuid.UUID {
	return uuid.UUID(id)
}

func (id ID) String() string {
	return uuid.UUID(id).String()
}

func (id ID) Bytes() []byte {
	u := uuid.UUID(id)
	out := make([]byte, 16)
	copy(out, u[:])
	return out
}

func (id ID) Value() (driver.Value, error) {
	return id.Bytes(), nil
}

func (id *ID) Scan(src any) error {
	switch value := src.(type) {
	case []byte:
		parsed, err := IDFromBytes(value)
		if err != nil {
			return err
		}
		*id = parsed
		return nil
	case string:
		parsed, err := ParseID(value)
		if err != nil {
			return err
		}
		*id = parsed
		return nil
	default:
		return fmt.Errorf("cannot scan %T into domain.ID", src)
	}
}

type AccountID = ID
type ProjectID = ID
type UserID = ID
type TransactionID = ID
type BillID = ID
