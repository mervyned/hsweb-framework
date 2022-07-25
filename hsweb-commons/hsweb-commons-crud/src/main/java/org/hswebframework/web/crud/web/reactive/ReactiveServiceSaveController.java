package org.hswebframework.web.crud.web.reactive;

import io.swagger.v3.oas.annotations.Operation;
import org.hswebframework.ezorm.rdb.mapping.ReactiveRepository;
import org.hswebframework.ezorm.rdb.mapping.defaults.SaveResult;
import org.hswebframework.web.api.crud.entity.RecordCreationEntity;
import org.hswebframework.web.api.crud.entity.RecordModifierEntity;
import org.hswebframework.web.authorization.Authentication;
import org.hswebframework.web.authorization.annotation.Authorize;
import org.hswebframework.web.authorization.annotation.SaveAction;
import org.hswebframework.web.crud.service.ReactiveCrudService;
import org.hswebframework.web.exception.NotFoundException;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * 响应式保存接口,基于{@link  ReactiveCrudService}提供默认的新增,保存,修改接口.
 *
 * @param <E> 实体类型
 * @param <K> 主键类型
 */
public interface ReactiveServiceSaveController<E, K> {

    @Authorize(ignore = true)
    ReactiveCrudService<E, K> getService();

    @Authorize(ignore = true)
    default E applyCreationEntity(Authentication authentication, E entity) {
        RecordCreationEntity creationEntity = ((RecordCreationEntity) entity);
        creationEntity.setCreateTimeNow();
        creationEntity.setCreatorId(authentication.getUser().getId());
        creationEntity.setCreatorName(authentication.getUser().getName());
        return entity;
    }

    @Authorize(ignore = true)
    default E applyModifierEntity(Authentication authentication, E entity) {
        RecordModifierEntity modifierEntity = ((RecordModifierEntity) entity);
        modifierEntity.setModifyTimeNow();
        modifierEntity.setModifierId(authentication.getUser().getId());
        modifierEntity.setModifierName(authentication.getUser().getName());
        return entity;
    }

    @Authorize(ignore = true)
    default E applyAuthentication(E entity, Authentication authentication) {
        if (entity instanceof RecordCreationEntity) {
            entity = applyCreationEntity(authentication, entity);
        }
        if (entity instanceof RecordModifierEntity) {
            entity = applyModifierEntity(authentication, entity);
        }
        return entity;
    }

    /**
     * 保存数据,如果传入了id,并且对应数据存在,则尝试覆盖,不存在则新增.
     * <br><br>
     * 以类注解{@code @RequestMapping("/api/test")}为例:
     * <pre>{@code
     *
     * PATCH /api/test
     * Content-Type: application/json
     *
     * [
     *  {
     *   "name":"value"
     *  }
     * ]
     * }
     * </pre>
     *
     * @param payload payload
     * @return 保存结果
     */
    @PatchMapping
    @SaveAction
    @Operation(summary = "Save data", description = "If an id is passed in and the corresponding data exists, an attempt is made to overwrite it, and if it does not exist, it is added.")
    default Mono<SaveResult> save(@RequestBody Flux<E> payload) {
        return Authentication
                .currentReactive()
                .flatMapMany(auth -> payload.map(entity -> applyAuthentication(entity, auth)))
                .switchIfEmpty(payload)
                .as(getService()::save);
    }

    /**
     * 批量新增
     * <br><br>
     * 以类注解{@code @RequestMapping("/api/test")}为例:
     * <pre>{@code
     *
     * POST /api/test/_batch
     * Content-Type: application/json
     *
     * [
     *  {
     *   "name":"value"
     *  }
     * ]
     * }
     * </pre>
     *
     * @param payload payload
     * @return 保存结果
     */
    @PostMapping("/_batch")
    @SaveAction
    @Operation(summary = "Add data in bulk")
    default Mono<Integer> add(@RequestBody Flux<E> payload) {

        return Authentication
                .currentReactive()
                .flatMapMany(auth -> payload.map(entity -> applyAuthentication(entity, auth)))
                .switchIfEmpty(payload)
                .collectList()
                .as(getService()::insertBatch);
    }

    /**
     * 新增单个数据,并返回新增后的数据.
     * <br><br>
     * 以类注解{@code @RequestMapping("/api/test")}为例:
     * <pre>{@code
     *
     * POST /api/test
     * Content-Type: application/json
     *
     *  {
     *   "name":"value"
     *  }
     * }
     * </pre>
     *
     * @param payload payload
     * @return 新增后的数据
     */
    @PostMapping
    @SaveAction
    @Operation(summary = "Add a single piece of data and return the added data.")
    default Mono<E> add(@RequestBody Mono<E> payload) {
        return Authentication
                .currentReactive()
                .flatMap(auth -> payload.map(entity -> applyAuthentication(entity, auth)))
                .switchIfEmpty(payload)
                .flatMap(entity -> getService().insert(Mono.just(entity)).thenReturn(entity));
    }

    /**
     * 根据ID修改数据
     * <br><br>
     * 以类注解{@code @RequestMapping("/api/test")}为例:
     * <pre>{@code
     *
     * PUT /api/test/{id}
     * Content-Type: application/json
     *
     *  {
     *   "name":"value"
     *  }
     * }
     * </pre>
     *
     * @param payload payload
     * @return 是否成功
     */
    @PutMapping("/{id}")
    @SaveAction
    @Operation(summary = "Modify the data according to the ID")
    default Mono<Boolean> update(@PathVariable K id, @RequestBody Mono<E> payload) {

        return Authentication
                .currentReactive()
                .flatMap(auth -> payload.map(entity -> applyAuthentication(entity, auth)))
                .switchIfEmpty(payload)
                .flatMap(entity -> getService().updateById(id, Mono.just(entity)))
                .doOnNext(i -> {
                    if (i == 0) {
                        throw new NotFoundException();
                    }
                })
                .thenReturn(true);

    }
}
