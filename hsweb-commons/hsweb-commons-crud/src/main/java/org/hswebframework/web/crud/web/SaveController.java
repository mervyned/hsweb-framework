package org.hswebframework.web.crud.web;

import io.swagger.v3.oas.annotations.Operation;
import org.hswebframework.ezorm.rdb.mapping.SyncRepository;
import org.hswebframework.ezorm.rdb.mapping.defaults.SaveResult;
import org.hswebframework.web.api.crud.entity.RecordCreationEntity;
import org.hswebframework.web.api.crud.entity.RecordModifierEntity;
import org.hswebframework.web.authorization.Authentication;
import org.hswebframework.web.authorization.annotation.Authorize;
import org.hswebframework.web.authorization.annotation.SaveAction;
import org.springframework.web.bind.annotation.*;

import java.util.List;

public interface SaveController<E, K> {

    @Authorize(ignore = true)
    SyncRepository<E, K> getRepository();

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

    @PatchMapping
    @SaveAction
    @Operation(summary = "Save data", description = "If an id is passed in and the corresponding data exists, an attempt is made to overwrite it, and if it does not exist, it is added.")
    default SaveResult save(@RequestBody List<E> payload) {
        return getRepository()
                .save(Authentication
                              .current()
                              .map(auth -> {
                                  for (E e : payload) {
                                      applyAuthentication(e, auth);
                                  }
                                  return payload;
                              })
                              .orElse(payload)
                );
    }

    @PostMapping("/_batch")
    @SaveAction
    @Operation(summary = "Add data in bulk")
    default int add(@RequestBody List<E> payload) {
        return getRepository()
                .insertBatch(Authentication
                                     .current()
                                     .map(auth -> {
                                         for (E e : payload) {
                                             applyAuthentication(e, auth);
                                         }
                                         return payload;
                                     })
                                     .orElse(payload)
                );
    }

    @PostMapping
    @SaveAction
    @Operation(summary = "Add a single piece of data and return the added data.")
    default E add(@RequestBody E payload) {
        this.getRepository()
            .insert(Authentication
                            .current()
                            .map(auth -> applyAuthentication(payload, auth))
                            .orElse(payload));
        return payload;
    }


    @PutMapping("/{id}")
    @SaveAction
    @Operation(summary = "Modify the data according to the ID")
    default boolean update(@PathVariable K id, @RequestBody E payload) {

        return getRepository()
                .updateById(id, Authentication
                        .current()
                        .map(auth -> applyAuthentication(payload, auth))
                        .orElse(payload))
                > 0;

    }
}
