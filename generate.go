package pteron

//go:generate go run github.com/oapi-codegen/oapi-codegen/v2/cmd/oapi-codegen --config api/openapi/internal.oapi.yaml api/openapi/internal.yaml
//go:generate go run github.com/oapi-codegen/oapi-codegen/v2/cmd/oapi-codegen --config api/openapi/public.oapi.yaml api/openapi/pteron.yaml
//go:generate sh -c "mkdir -p .bin && GOBIN=$PWD/.bin go install google.golang.org/protobuf/cmd/protoc-gen-go google.golang.org/grpc/cmd/protoc-gen-go-grpc && PATH=$PWD/.bin:$PATH protoc --proto_path=api/proto --go_out=. --go_opt=module=github.com/traP-jp/pteron-server --go-grpc_out=. --go-grpc_opt=module=github.com/traP-jp/pteron-server api/proto/cornucopia.proto"
