package org.hswebframework.web.system.authorization.defaults.webflux;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.hswebframework.web.authorization.annotation.Authorize;
import org.hswebframework.web.authorization.annotation.DeleteAction;
import org.hswebframework.web.authorization.annotation.Resource;
import org.hswebframework.web.crud.service.ReactiveCrudService;
import org.hswebframework.web.crud.web.reactive.ReactiveServiceCrudController;
import org.hswebframework.web.system.authorization.api.entity.DimensionUserEntity;
import org.hswebframework.web.system.authorization.defaults.service.DefaultDimensionUserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.List;

@RestController
@RequestMapping("/dimension-user")
@Authorize
@Resource(id = "dimension", name = "权限维度管理", group = "system")
@Tag(name = "Dimension Type")
public class WebFluxDimensionUserController implements ReactiveServiceCrudController<DimensionUserEntity, String> {

    @Autowired
    private DefaultDimensionUserService dimensionUserService;

    @Override
    public ReactiveCrudService<DimensionUserEntity, String> getService() {
        return dimensionUserService;
    }


    @DeleteAction
    @DeleteMapping("/user/{userId}/dimension/{dimensionId}")
    @Operation(summary = "Deassociates a specified dimension from a user")
    public Mono<Integer> deleteByUserAndDimensionId(@PathVariable
                                                    @Parameter(description = "用户ID") String userId,
                                                    @PathVariable
                                                    @Parameter(description = "维度ID") String dimensionId) {
        return dimensionUserService
                .createDelete()
                .where(DimensionUserEntity::getUserId, userId)
                .and(DimensionUserEntity::getDimensionId, dimensionId)
                .execute();
    }

    @DeleteAction
    @DeleteMapping("/user/{userId}")
    @Operation(summary = "Deassociate all dimensions from the user")
    public Mono<Integer> deleteByUserId(@PathVariable
                                        @Parameter(description = "用户ID") String userId) {
        return dimensionUserService
                .createDelete()
                .where(DimensionUserEntity::getUserId, userId)
                .execute();
    }

    @DeleteAction
    @DeleteMapping("/dimension/{dimensionId}")
    @Operation(summary = "Unassociates all users from the specified dimension")
    public Mono<Integer> deleteByDimension(@PathVariable
                                           @Parameter(description = "维度ID") String dimensionId) {
        return dimensionUserService
                .createDelete()
                .where(DimensionUserEntity::getDimensionId, dimensionId)
                .execute();
    }

    @DeleteAction
    @PostMapping("/user/{dimensionType}/{dimensionId}/_unbind")
    @Operation(summary = "Deassociates a specified dimension from a user")
    public Mono<Integer> deleteUserDimension(@PathVariable
                                             @Parameter(description = "维度类型,比如: role") String dimensionType,
                                             @PathVariable
                                             @Parameter(description = "维度ID,比如: 角色ID") String dimensionId,
                                             @Parameter(description = "用户ID") @RequestBody Mono<List<String>> userId) {
        return userId
                .flatMap(userIdList -> dimensionUserService
                        .createDelete()
                        .where(DimensionUserEntity::getDimensionId, dimensionId)
                        .and(DimensionUserEntity::getDimensionTypeId, dimensionType)
                        .in(DimensionUserEntity::getUserId, userIdList)
                        .execute());
    }
}
