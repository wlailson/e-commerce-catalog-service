package io.wlailson.github.e_commerce_catalog_service.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.wlailson.github.e_commerce_catalog_service.dto.ProductDTO;
import io.wlailson.github.e_commerce_catalog_service.dto.ProductMinDTO;
import io.wlailson.github.e_commerce_catalog_service.service.ProductService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;

@RestController
@RequestMapping("/products")
@RequiredArgsConstructor
@Tag(name = "Produtos", description = "Consulta e manutenção do catálogo de produtos")
@SecurityScheme(
        name = "bearerAuth",
        type = SecuritySchemeType.HTTP,
        scheme = "bearer",
        bearerFormat = "JWT"
)
public class ProductController {

    private final ProductService service;

    @GetMapping("/{productId}")
    @Operation(summary = "Buscar produto por ID", description = "Retorna os dados completos de um produto.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Produto encontrado"),
            @ApiResponse(responseCode = "404", description = "Produto não encontrado",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    public ResponseEntity<ProductDTO> findById(
            @Parameter(description = "Identificador do produto", example = "1")
            @PathVariable Long productId) {
        ProductDTO response = service.findById(productId);
        return ResponseEntity.ok(response);
    }

    @GetMapping
    @Operation(summary = "Listar produtos", description = "Lista os produtos paginados, com filtro opcional pelo nome.")
    @ApiResponse(responseCode = "200", description = "Página de produtos retornada")
    public ResponseEntity<Page<ProductMinDTO>> findAll(
            @Parameter(description = "Texto usado para filtrar produtos pelo nome")
            @RequestParam(name = "name", defaultValue = "") String name,
            @ParameterObject
            Pageable pageable) {
        Page<ProductMinDTO> response = service.findAll(name, pageable);
        return ResponseEntity.ok(response);
    }

    @PreAuthorize("isAuthenticated() and hasAuthority('ROLE_ADMIN')")
    @PostMapping
    @Operation(
            summary = "Cadastrar produto",
            description = "Cria um produto. Requer autenticação Bearer JWT com a autoridade ROLE_ADMIN.",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Produto cadastrado"),
            @ApiResponse(responseCode = "400", description = "Dados inválidos",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "401", description = "Autenticação ausente ou inválida"),
            @ApiResponse(responseCode = "403", description = "Usuário sem a autoridade ROLE_ADMIN"),
            @ApiResponse(responseCode = "404", description = "Categoria informada não encontrada",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    public ResponseEntity<ProductDTO> insert(
            @Valid
            @RequestBody ProductDTO request) {

        ProductDTO response = service.insert(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").buildAndExpand(response.id()).toUri();
        return ResponseEntity.created(location).body(response);
    }

    @PreAuthorize("isAuthenticated() and hasAuthority('ROLE_ADMIN')")
    @PutMapping("/{productId}")
    @Operation(
            summary = "Atualizar produto",
            description = "Atualiza os dados de um produto. Requer autenticação Bearer JWT com a autoridade ROLE_ADMIN.",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Produto atualizado"),
            @ApiResponse(responseCode = "400", description = "Dados inválidos",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "401", description = "Autenticação ausente ou inválida"),
            @ApiResponse(responseCode = "403", description = "Usuário sem a autoridade ROLE_ADMIN"),
            @ApiResponse(responseCode = "404", description = "Produto ou categoria não encontrado",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    public ResponseEntity<ProductDTO> update(
            @Parameter(description = "Identificador do produto", example = "1")
            @PathVariable
            Long productId,
            @Valid
            @RequestBody
            ProductDTO request) {
        ProductDTO response = service.update(productId, request);
        return ResponseEntity.ok(response);
    }

    @PreAuthorize("isAuthenticated() and hasAuthority('ROLE_ADMIN')")
    @DeleteMapping("/{productId}")
    @Operation(
            summary = "Excluir produto",
            description = "Remove um produto. Requer autenticação Bearer JWT com a autoridade ROLE_ADMIN.",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Produto excluído"),
            @ApiResponse(responseCode = "401", description = "Autenticação ausente ou inválida"),
            @ApiResponse(responseCode = "403", description = "Usuário sem a autoridade ROLE_ADMIN"),
            @ApiResponse(responseCode = "404", description = "Produto não encontrado ou falha de integridade",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    public ResponseEntity<Void> deleteById(
            @Parameter(description = "Identificador do produto", example = "1")
            @PathVariable Long productId) {
        service.deleteById(productId);
        return ResponseEntity.noContent().build();
    }
}
