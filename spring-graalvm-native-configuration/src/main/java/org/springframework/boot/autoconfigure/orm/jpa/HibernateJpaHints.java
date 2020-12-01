/*
 * Copyright 2020 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.boot.autoconfigure.orm.jpa;

import java.util.EventListener;

import javax.persistence.Column;
import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.MappedSuperclass;
import javax.persistence.PersistenceContext;
import javax.persistence.Table;
import javax.persistence.TableGenerator;
import javax.persistence.Transient;

import com.zaxxer.hikari.util.ConcurrentBag;
import org.apache.logging.log4j.message.DefaultFlowMessageFactory;
import org.apache.logging.log4j.message.ParameterizedMessageFactory;
import org.apache.logging.log4j.message.ReusableMessageFactory;
import org.hibernate.Session;
import org.hibernate.annotations.Tuplizer;
import org.hibernate.cache.spi.access.CollectionDataAccess;
import org.hibernate.dialect.H2Dialect;
import org.hibernate.dialect.HSQLDialect;
import org.hibernate.dialect.MySQLDialect;
import org.hibernate.engine.transaction.jta.platform.internal.NoJtaPlatform;
import org.hibernate.event.spi.AutoFlushEventListener;
import org.hibernate.event.spi.ClearEventListener;
import org.hibernate.event.spi.DeleteEventListener;
import org.hibernate.event.spi.DirtyCheckEventListener;
import org.hibernate.event.spi.EventType;
import org.hibernate.event.spi.EvictEventListener;
import org.hibernate.event.spi.FlushEntityEventListener;
import org.hibernate.event.spi.FlushEventListener;
import org.hibernate.event.spi.InitializeCollectionEventListener;
import org.hibernate.event.spi.LoadEventListener;
import org.hibernate.event.spi.LockEventListener;
import org.hibernate.event.spi.MergeEventListener;
import org.hibernate.event.spi.PersistEventListener;
import org.hibernate.event.spi.PostCollectionRecreateEventListener;
import org.hibernate.event.spi.PostCollectionRemoveEventListener;
import org.hibernate.event.spi.PostCollectionUpdateEventListener;
import org.hibernate.event.spi.PostDeleteEventListener;
import org.hibernate.event.spi.PostInsertEventListener;
import org.hibernate.event.spi.PostLoadEventListener;
import org.hibernate.event.spi.PostUpdateEventListener;
import org.hibernate.event.spi.PreCollectionRecreateEventListener;
import org.hibernate.event.spi.PreCollectionRemoveEventListener;
import org.hibernate.event.spi.PreCollectionUpdateEventListener;
import org.hibernate.event.spi.PreDeleteEventListener;
import org.hibernate.event.spi.PreInsertEventListener;
import org.hibernate.event.spi.PreLoadEventListener;
import org.hibernate.event.spi.PreUpdateEventListener;
import org.hibernate.event.spi.RefreshEventListener;
import org.hibernate.event.spi.ReplicateEventListener;
import org.hibernate.event.spi.ResolveNaturalIdEventListener;
import org.hibernate.event.spi.SaveOrUpdateEventListener;
import org.hibernate.hql.internal.antlr.HqlTokenTypes;
import org.hibernate.hql.internal.antlr.SqlTokenTypes;
import org.hibernate.hql.internal.ast.HqlToken;
import org.hibernate.hql.internal.ast.tree.BinaryLogicOperatorNode;
import org.hibernate.hql.internal.ast.tree.DotNode;
import org.hibernate.hql.internal.ast.tree.FromClause;
import org.hibernate.hql.internal.ast.tree.FromElement;
import org.hibernate.hql.internal.ast.tree.IdentNode;
import org.hibernate.hql.internal.ast.tree.Node;
import org.hibernate.hql.internal.ast.tree.OrderByClause;
import org.hibernate.hql.internal.ast.tree.ParameterNode;
import org.hibernate.hql.internal.ast.tree.QueryNode;
import org.hibernate.hql.internal.ast.tree.SelectClause;
import org.hibernate.hql.internal.ast.tree.SelectExpressionImpl;
import org.hibernate.hql.internal.ast.tree.SqlFragment;
import org.hibernate.hql.internal.ast.tree.SqlNode;
import org.hibernate.id.Assigned;
import org.hibernate.id.ForeignGenerator;
import org.hibernate.id.GUIDGenerator;
import org.hibernate.id.IdentityGenerator;
import org.hibernate.id.IncrementGenerator;
import org.hibernate.id.SelectGenerator;
import org.hibernate.id.SequenceHiLoGenerator;
import org.hibernate.id.SequenceIdentityGenerator;
import org.hibernate.id.UUIDGenerator;
import org.hibernate.id.UUIDHexGenerator;
import org.hibernate.id.enhanced.SequenceStyleGenerator;
import org.hibernate.internal.CoreMessageLogger;
import org.hibernate.internal.EntityManagerMessageLogger;
import org.hibernate.internal.SessionImpl;
import org.hibernate.jpa.HibernateEntityManager;
import org.hibernate.jpa.HibernateEntityManagerFactory;
import org.hibernate.mapping.PersistentClass;
import org.hibernate.persister.collection.BasicCollectionPersister;
import org.hibernate.persister.collection.OneToManyPersister;
import org.hibernate.persister.entity.JoinedSubclassEntityPersister;
import org.hibernate.persister.entity.SingleTableEntityPersister;
import org.hibernate.persister.entity.UnionSubclassEntityPersister;
import org.hibernate.persister.spi.PersisterCreationContext;
import org.hibernate.query.QueryProducer;
import org.hibernate.resource.transaction.backend.jdbc.internal.JdbcResourceLocalTransactionCoordinatorBuilderImpl;
import org.hibernate.sql.ordering.antlr.GeneratedOrderByFragmentRendererTokenTypes;
import org.hibernate.tuple.entity.AbstractEntityTuplizer;
import org.hibernate.tuple.entity.EntityTuplizer;
import org.hibernate.tuple.entity.PojoEntityTuplizer;

import org.springframework.boot.orm.jpa.hibernate.SpringImplicitNamingStrategy;
import org.springframework.boot.orm.jpa.hibernate.SpringPhysicalNamingStrategy;
import org.springframework.data.jpa.repository.support.SimpleJpaRepository;
import org.springframework.nativex.extension.NativeImageConfiguration;
import org.springframework.nativex.extension.NativeImageHint;
import org.springframework.nativex.extension.ResourcesInfo;
import org.springframework.nativex.extension.TypeInfo;
import org.springframework.nativex.type.AccessBits;
import org.springframework.orm.jpa.support.PersistenceAnnotationBeanPostProcessor;
import org.springframework.stereotype.Repository;

@SuppressWarnings("deprecation")
@NativeImageHint(trigger=HibernateJpaAutoConfiguration.class,typeInfos= {
		@TypeInfo(types= {DefaultFlowMessageFactory.class, ConcurrentBag.IConcurrentBagEntry[].class, ConcurrentBag.IConcurrentBagEntry.class})
})
@NativeImageHint(trigger=HibernateJpaConfiguration.class,
	resourcesInfos = {
			@ResourcesInfo(patterns={"hibernate.properties","org/hibernate/.*.xsd","org/hibernate/.*.dtd"})
	},
	typeInfos= {
		@TypeInfo(types= {
				// petclinic
				// These three are due to org.hibernate.hql.internal.ast.util.TokenPrinters
				HqlTokenTypes.class,SqlTokenTypes.class, GeneratedOrderByFragmentRendererTokenTypes.class,
				// TODO what about having a way to specify everything in a package? More resilient and less verbose? do those things matter?
				OrderByClause.class,SelectExpressionImpl.class,SqlFragment.class,SelectClause.class,BinaryLogicOperatorNode.class,
				ParameterNode.class,DotNode.class,IdentNode.class,FromElement.class,QueryNode.class,SqlNode.class,FromClause.class,
				Node.class,HqlToken.class,
		},access=AccessBits.CLASS|AccessBits.DECLARED_CONSTRUCTORS|AccessBits.DECLARED_METHODS|AccessBits.DECLARED_FIELDS),
		@TypeInfo(types= {
				// petclinic
				Repository.class,
				PersistenceContext.class,MappedSuperclass.class,Column.class,ManyToOne.class,JoinColumn.class,Table.class,Transient.class
		},access=AccessBits.CLASS|AccessBits.DECLARED_METHODS),
		@TypeInfo(types= {
				OneToManyPersister.class,JoinedSubclassEntityPersister.class,SingleTableEntityPersister.class,UnionSubclassEntityPersister.class,
				BasicCollectionPersister.class,
				H2Dialect.class,
				MySQLDialect.class,
				HSQLDialect.class,
				SessionImpl.class,
				EventType.class,
				// Related to EventListenerRegistry...
				SaveOrUpdateEventListener.class,ResolveNaturalIdEventListener.class,
				ReplicateEventListener.class,RefreshEventListener.class,PreUpdateEventListener.class,
				PreLoadEventListener.class,PreInsertEventListener.class,PreDeleteEventListener.class,
				PreCollectionUpdateEventListener.class,PreCollectionRecreateEventListener.class,
				PreCollectionRemoveEventListener.class,PostUpdateEventListener.class,
				PostLoadEventListener.class,PostInsertEventListener.class,PostDeleteEventListener.class,
				PostCollectionUpdateEventListener.class,PostCollectionRemoveEventListener.class,
				PostCollectionRecreateEventListener.class,PersistEventListener.class,MergeEventListener.class,
				LockEventListener.class,LoadEventListener.class,InitializeCollectionEventListener.class,
				FlushEventListener.class,FlushEntityEventListener.class,EvictEventListener.class,
				DirtyCheckEventListener.class,DeleteEventListener.class,ClearEventListener.class,
				AutoFlushEventListener.class,
				ReplicateEventListener[].class,
				PostInsertEventListener[].class,PostLoadEventListener[].class,PostUpdateEventListener[].class,
				PreCollectionRecreateEventListener[].class,PreCollectionRemoveEventListener[].class,PreCollectionUpdateEventListener[].class,
				PreLoadEventListener[].class,PreUpdateEventListener[].class,RefreshEventListener[].class,SaveOrUpdateEventListener[].class,
				LockEventListener[].class,InitializeCollectionEventListener[].class,ResolveNaturalIdEventListener[].class,
				LoadEventListener[].class,FlushEntityEventListener[].class,FlushEventListener[].class,
				EvictEventListener[].class,DirtyCheckEventListener[].class,DeleteEventListener[].class,
				AutoFlushEventListener[].class,PersistEventListener[].class,EventType[].class,
				ClearEventListener[].class,MergeEventListener[].class,PostCollectionRecreateEventListener[].class,
				PostCollectionRemoveEventListener[].class,PostCollectionUpdateEventListener[].class,PostDeleteEventListener[].class,
			
				// These from EntityTuplizerFactory
				Tuplizer.class,EntityTuplizer.class,AbstractEntityTuplizer.class,PojoEntityTuplizer.class,

				JdbcResourceLocalTransactionCoordinatorBuilderImpl.class,
				SimpleJpaRepository.class,
				EntityManagerFactory.class,
				EntityManager.class,
				QueryProducer.class,
				HibernateEntityManager.class,
				HibernateEntityManagerFactory.class,
				GeneratedValue.class,Id.class,
				ReusableMessageFactory.class,
				// TODO expose package contents again?
				ParameterizedMessageFactory.class,
				PersistenceAnnotationBeanPostProcessor.class,
				// From DefaultIdentifierGeneratorFactory?
				UUIDGenerator.class,GUIDGenerator.class,UUIDHexGenerator.class,Assigned.class,IdentityGenerator.class,
				SelectGenerator.class,SequenceStyleGenerator.class,SequenceHiLoGenerator.class,IncrementGenerator.class,
				ForeignGenerator.class,SequenceIdentityGenerator.class,TableGenerator.class,

			SingleTableEntityPersister.class,CollectionDataAccess.class,PersistentClass.class,
			PersisterCreationContext.class,

			DataSourceInitializedPublisher.class,
			// related to Naming.applyNamingStrategies
			SpringImplicitNamingStrategy.class,SpringPhysicalNamingStrategy.class,
			
		//	HikariDataSource.class,HikariConfig.class,	MVTableEngine.class // pushed to datasource hints
			NoJtaPlatform.class,
			//org.hibernate.service.jta.platform.internal.NoJtaPlatform.class
			EntityManagerMessageLogger.class,CoreMessageLogger.class,
			Session.class,EventListener.class
		}, typeNames = {
			"org.hibernate.internal.EntityManagerMessageLogger_$logger",
			"org.hibernate.internal.CoreMessageLogger_$logger",
			"org.hibernate.service.jta.platform.internal.NoJtaPlatform",
			"org.hibernate.annotations.common.util.impl.Log_$logger",
			"org.hibernate.annotations.common.util.impl.Log",
		})
})
public class HibernateJpaHints implements NativeImageConfiguration {
}
